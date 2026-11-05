<#
.SYNOPSIS
    Copia la base local (MySQL de este equipo) a la VM de GCP. Se corre una vez, al pasar a la nube.

.DESCRIPTION
    1. Revisa que la base de la VM esté vacía (o solo con el esquema que crea Flyway). Si ya tiene
       clientes o pagos se detiene, salvo que se pase -Reemplazar.
    2. mysqldump de la base local a un archivo temporal (con datos personales: se borra al final y
       nunca entra al repo).
    3. Abre el túnel de IAP a la VM, carga el archivo y compara el número de filas de cada tabla.
    Incluye flyway_schema_history, así que al arrancar el back Flyway ve la base ya en la última
    versión y no toca nada.

    Antes de correrlo: deja de registrar pagos o préstamos en la base local. Después, la base buena
    es la de la VM; la local queda solo para desarrollo.

.EXAMPLE
    .\deploy\migrar-bd.ps1
.EXAMPLE
    .\deploy\migrar-bd.ps1 -Reemplazar     # la VM ya tiene datos y se quieren sustituir por los locales
#>
[CmdletBinding()]
param(
    [switch] $Reemplazar,
    [string] $HostLocal = '127.0.0.1',
    [int] $PuertoLocal = 3306,
    [string] $UsuarioLocal = 'root',
    [string] $BaseLocal = 'prestamos',
    [int] $PuertoTunel = 3307,
    [string] $ArchivoVariables = (Join-Path $PSScriptRoot 'variables.psd1')
)

. "$PSScriptRoot\comun.ps1"
$v = Read-Variables $ArchivoVariables
$raiz = Split-Path -Parent $PSScriptRoot

function Find-Cliente([string] $Nombre) {
    $cmd = Get-Command $Nombre -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $ruta = Get-ChildItem 'C:\Program Files\MySQL' -Recurse -Filter "$Nombre.exe" -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($ruta) { return $ruta.FullName }
    throw "No se encontró $Nombre.exe (viene con MySQL Server o MySQL Shell)."
}
$mysqldump = Find-Cliente 'mysqldump'
$mysql = Find-Cliente 'mysql'
if (-not (Test-Comando { gcloud --version })) { throw 'No se encontró gcloud (Google Cloud CLI).' }
if (Get-NetTCPConnection -LocalPort $PuertoTunel -State Listen -ErrorAction SilentlyContinue) {
    throw "El puerto $PuertoTunel ya está en uso (¿tunel-bd.ps1 abierto?). Ciérralo o usa -PuertoTunel."
}

# Contraseña local: la misma que usa el back en desarrollo (config/application-local.properties).
$propsLocal = Join-Path $raiz 'config\application-local.properties'
$pwLocal = $null
if (Test-Path $propsLocal) {
    $linea = Get-Content $propsLocal | Where-Object { $_ -match '^\s*DB_PASSWORD\s*=' } | Select-Object -First 1
    if ($linea) { $pwLocal = ($linea -replace '^\s*DB_PASSWORD\s*=', '').Trim() }
}
if (-not $pwLocal) {
    $seguro = Read-Host "Contraseña de $UsuarioLocal en el MySQL local" -AsSecureString
    $pwLocal = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro))
}
$pwVm = (gcloud secrets versions access latest --secret=db-password --project=$($v.ProyectoGcp))
if ($LASTEXITCODE -ne 0 -or -not $pwVm) { throw 'No se pudo leer db-password de Secret Manager (¿corriste preparar-gcp.ps1 y gcloud auth login?).' }

# Las contraseñas van por MYSQL_PWD (variable de este proceso) para que no aparezcan en la línea de comandos.
function Invoke-Local([string[]] $Argumentos) {
    $env:MYSQL_PWD = $pwLocal
    try { & $mysql -h $HostLocal -P $PuertoLocal -u $UsuarioLocal --default-character-set=utf8mb4 @Argumentos }
    finally { Remove-Item env:MYSQL_PWD -ErrorAction SilentlyContinue }
}
function Invoke-Vm([string[]] $Argumentos) {
    $env:MYSQL_PWD = $pwVm
    try { & $mysql -h 127.0.0.1 -P $PuertoTunel -u $v.UsuarioBd --ssl-mode=REQUIRED --default-character-set=utf8mb4 @Argumentos }
    finally { Remove-Item env:MYSQL_PWD -ErrorAction SilentlyContinue }
}
# Filas exactas por tabla (information_schema.table_rows es solo una estimación).
function Get-Conteos([scriptblock] $Ejecutar, [string] $Base) {
    $tablas = & $Ejecutar @('-N', '-B', '-e', "SELECT table_name FROM information_schema.tables WHERE table_schema='$Base' AND table_type='BASE TABLE' ORDER BY 1")
    if ($LASTEXITCODE -ne 0) { throw "No se pudo listar las tablas de $Base." }
    $conteos = [ordered]@{}
    foreach ($t in @($tablas | Where-Object { $_ })) {
        $conteos[$t] = [long] (& $Ejecutar @('-N', '-B', '-e', "SELECT COUNT(*) FROM ``$Base``.``$t``"))
    }
    return $conteos
}

$dump = Join-Path ([IO.Path]::GetTempPath()) ("crediexpress-migracion-{0:yyyyMMdd-HHmmss}.sql" -f (Get-Date))
$tunel = $null
try {
    $origen = Get-Conteos ${function:Invoke-Local} $BaseLocal
    if (-not $origen.Contains('flyway_schema_history')) { throw "La base local $BaseLocal no tiene flyway_schema_history; ¿es la base correcta?" }
    Write-Host "Base local ${BaseLocal}: $(($origen.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ', ')"

    Invoke-Paso 'Respaldar la base local (mysqldump)' {
        $env:MYSQL_PWD = $pwLocal
        try {
            # Sin --databases: el archivo no lleva CREATE DATABASE/USE y se carga en la base de la VM.
            & $mysqldump -h $HostLocal -P $PuertoLocal -u $UsuarioLocal --single-transaction --quick `
                --routines --triggers --events --hex-blob --no-tablespaces --set-gtid-purged=OFF `
                --default-character-set=utf8mb4 --result-file=$dump $BaseLocal
        }
        finally { Remove-Item env:MYSQL_PWD -ErrorAction SilentlyContinue }
    }

    Write-Host '==> Abriendo túnel a la VM' -ForegroundColor Cyan
    $gcloud = (Get-Command gcloud).Source
    if ($gcloud -notmatch '\.(cmd|exe)$') { $gcloud = [IO.Path]::ChangeExtension($gcloud, '.cmd') }
    $tunel = Start-Process -FilePath $gcloud -PassThru -WindowStyle Hidden -ArgumentList @(
        'compute', 'start-iap-tunnel', $v.Vm, '3306', "--local-host-port=localhost:$PuertoTunel",
        "--zone=$($v.Zona)", "--project=$($v.ProyectoGcp)")
    $listo = $false
    foreach ($i in 1..30) {
        Start-Sleep -Seconds 2
        if ($tunel.HasExited) { throw "El túnel se cerró (código $($tunel.ExitCode)). Prueba .\deploy\tunel-bd.ps1 para ver el error." }
        $tcp = New-Object Net.Sockets.TcpClient
        try { $tcp.Connect('127.0.0.1', $PuertoTunel); $listo = $true } catch { } finally { $tcp.Dispose() }
        if ($listo) { break }
    }
    if (-not $listo) { throw "El túnel no abrió el puerto $PuertoTunel en 60 s." }

    $destino = Get-Conteos ${function:Invoke-Vm} $v.BaseDatos
    $conDatos = @($destino.GetEnumerator() | Where-Object { $_.Key -ne 'flyway_schema_history' -and $_.Value -gt 0 })
    if ($conDatos.Count -gt 0 -and -not $Reemplazar) {
        throw "La base de la VM ya tiene datos ($(($conDatos | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ', ')). Si quieres sustituirlos por los locales, corre con -Reemplazar."
    }

    Invoke-Paso 'Cargar en la VM' {
        # Cada tabla del archivo hace DROP TABLE IF EXISTS + CREATE, así que reemplaza lo que haya.
        Invoke-Vm @($v.BaseDatos, '-e', "source $($dump -replace '\\', '/')")
    }

    $final = Get-Conteos ${function:Invoke-Vm} $v.BaseDatos
    $diferencias = @($origen.Keys | Where-Object { -not $final.Contains($_) -or $final[$_] -ne $origen[$_] })
    if ($diferencias.Count -gt 0) {
        throw "Las filas no coinciden en: $($diferencias -join ', '). Revisa la carga antes de usar la VM."
    }
    Write-Host "Migración completa: $($origen.Count) tablas con las mismas filas que la base local." -ForegroundColor Green
    Write-Host 'Desde ahora registra pagos y préstamos solo en la nube.' -ForegroundColor Green
}
finally {
    if ($tunel -and -not $tunel.HasExited) { Stop-Process -Id $tunel.Id -Force -ErrorAction SilentlyContinue }
    # gcloud.cmd lanza python como hijo; se cierra el que tenga el puerto del túnel.
    Get-NetTCPConnection -LocalPort $PuertoTunel -State Listen -ErrorAction SilentlyContinue |
        ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
    if (Test-Path $dump) { Remove-Item $dump -Force }
    Remove-Variable pwLocal, pwVm -ErrorAction SilentlyContinue
}

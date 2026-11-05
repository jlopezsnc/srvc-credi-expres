# Funciones compartidas por los scripts de deploy/. Se carga con: . "$PSScriptRoot\comun.ps1"

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Read-Variables([string] $Archivo) {
    if (-not (Test-Path $Archivo)) { throw "No existe el archivo de variables: $Archivo" }
    $vars = Import-PowerShellDataFile $Archivo
    $pendientes = @($vars.GetEnumerator() | Where-Object { $_.Value -eq 'CAMBIAR' } | ForEach-Object { $_.Key })
    if ($pendientes.Count -gt 0) {
        throw "Faltan variables en ${Archivo}: $($pendientes -join ', ')"
    }
    return $vars
}

# Ejecuta un comando en silencio y regresa $true si terminó bien. En PowerShell 5.1, redirigir la
# salida de error de un ejecutable con ErrorActionPreference=Stop lanza excepción; aquí se evita.
function Test-Comando([scriptblock] $Comando) {
    $anterior = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $Comando *> $null
        return ($LASTEXITCODE -eq 0)
    }
    finally {
        $ErrorActionPreference = $anterior
    }
}

function Invoke-Paso([string] $Nombre, [scriptblock] $Accion) {
    Write-Host "==> $Nombre" -ForegroundColor Cyan
    & $Accion
    if ($LASTEXITCODE -ne 0) { throw "Fallo: $Nombre (codigo $LASTEXITCODE). Se detiene el proceso." }
}

# Nombres derivados de variables.psd1 que usan varios scripts.
function Get-Nombres([hashtable] $v) {
    return @{
        SaBack     = "crediexpress-back@$($v.ProyectoGcp).iam.gserviceaccount.com"
        SaVm       = "crediexpress-vm@$($v.ProyectoGcp).iam.gserviceaccount.com"
        IpVm       = "$($v.Vm)-ip"
        TagVm      = 'crediexpress-mysql'
        Repositorio = "$($v.Region)-docker.pkg.dev/$($v.ProyectoGcp)/crediexpress"
    }
}

# Estado que publica deploy/vm/instalar-mysql.sh (instalando | listo | error en la linea N).
function Get-EstadoVm([hashtable] $v) {
    $anterior = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $valor = gcloud compute instances get-guest-attributes $v.Vm --zone=$($v.Zona) `
            --query-path=crediexpress/estado --format='value(value)' 2>$null
        if ($LASTEXITCODE -ne 0) { return '' }
        return [string] $valor
    }
    finally {
        $ErrorActionPreference = $anterior
    }
}

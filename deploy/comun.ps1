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

# Reemplaza los marcadores __NOMBRE__ de los manifiestos con los valores de variables.psd1.
function Convert-Manifiesto([string] $Contenido, [hashtable] $Valores) {
    foreach ($par in $Valores.GetEnumerator()) {
        $Contenido = $Contenido.Replace("__$($par.Key)__", [string] $par.Value)
    }
    $sinReemplazar = [regex]::Matches($Contenido, '__[A-Z_]+__') | ForEach-Object { $_.Value } | Sort-Object -Unique
    if ($sinReemplazar) { throw "Marcadores sin valor en los manifiestos: $($sinReemplazar -join ', ')" }
    return $Contenido
}

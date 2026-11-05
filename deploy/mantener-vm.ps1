<#
.SYNOPSIS
    Instala las actualizaciones de seguridad de la VM de MySQL (correr una vez al mes).

.DESCRIPTION
    La VM no tiene IP externa, así que no puede descargar actualizaciones sola. Este script:
      1. Le pone una IP externa temporal.
      2. Entra por SSH (túnel de IAP) y corre apt-get upgrade.
      3. Si la actualización lo pide, programa un reinicio en 1 minuto (el back estará sin base
         ~1-2 min; conviene correrlo fuera del horario de cobranza).
      4. Le quita la IP externa, pase lo que pase.

.EXAMPLE
    .\deploy\mantener-vm.ps1
#>
[CmdletBinding()]
param(
    [string] $ArchivoVariables = (Join-Path $PSScriptRoot 'variables.psd1')
)

. "$PSScriptRoot\comun.ps1"
$v = Read-Variables $ArchivoVariables
$z = $v.Zona

Invoke-Paso 'Poner IP externa temporal' {
    gcloud compute instances add-access-config $v.Vm --zone=$z --project=$($v.ProyectoGcp) --access-config-name=temporal
}
try {
    Invoke-Paso 'Actualizar paquetes' {
        gcloud compute ssh $v.Vm --zone=$z --project=$($v.ProyectoGcp) --tunnel-through-iap --command=(
            'sudo DEBIAN_FRONTEND=noninteractive apt-get update -q && ' +
            'sudo DEBIAN_FRONTEND=noninteractive apt-get upgrade -y -q && ' +
            'if [ -f /var/run/reboot-required ]; then echo "Reinicio programado en 1 minuto"; sudo shutdown -r +1; fi'
        )
    }
}
finally {
    $acceso = gcloud compute instances describe $v.Vm --zone=$z --project=$($v.ProyectoGcp) --format='value(networkInterfaces[0].accessConfigs[0].name)'
    if ($acceso) {
        gcloud compute instances delete-access-config $v.Vm --zone=$z --project=$($v.ProyectoGcp) --access-config-name=$acceso
        if ($LASTEXITCODE -ne 0) { Write-Warning "No se pudo quitar la IP externa. Quítala a mano: gcloud compute instances delete-access-config $($v.Vm) --zone=$z --access-config-name=$acceso" }
    }
}
Write-Host 'VM actualizada.' -ForegroundColor Green

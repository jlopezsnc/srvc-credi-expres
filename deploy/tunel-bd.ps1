<#
.SYNOPSIS
    Abre un túnel seguro (IAP) desde tu equipo a MySQL en la VM, para MySQL Workbench.

.DESCRIPTION
    La VM no tiene IP externa: este túnel usa tu sesión de gcloud (gcloud auth login) y el rango de
    IAP que permite el firewall. Mientras la ventana siga abierta, MySQL queda en localhost:<Puerto>.
    Usuario: el de UsuarioBd en variables.psd1. La conexión debe usar SSL (Workbench: "Use SSL" =
    "Require"). La contraseña se consulta con:
        gcloud secrets versions access latest --secret=db-password

.EXAMPLE
    .\deploy\tunel-bd.ps1            # localhost:3307 (para no chocar con tu MySQL local en 3306)
#>
[CmdletBinding()]
param(
    [int] $Puerto = 3307,
    [string] $ArchivoVariables = (Join-Path $PSScriptRoot 'variables.psd1')
)

. "$PSScriptRoot\comun.ps1"
$v = Read-Variables $ArchivoVariables

Write-Host "MySQL de la VM en localhost:$Puerto (usuario $($v.UsuarioBd), base $($v.BaseDatos)). Ctrl+C para cerrar." -ForegroundColor Green
gcloud compute start-iap-tunnel $v.Vm 3306 --local-host-port="localhost:$Puerto" --zone=$($v.Zona) --project=$($v.ProyectoGcp)

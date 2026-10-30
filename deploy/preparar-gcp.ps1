<#
.SYNOPSIS
    Crea la infraestructura de GCP de Credi Express. Se corre UNA vez (si algo ya existe, lo salta).

.DESCRIPTION
    APIs, Artifact Registry, GKE Autopilot con Secret Manager, Cloud SQL (MySQL, IP privada),
    secreto de la base, cuenta de servicio con Workload Identity, Cloud Armor, política SSL e IP fija.
    Requiere gcloud con sesión iniciada (gcloud auth login) y permisos de Owner o Editor en el proyecto.

.EXAMPLE
    .\deploy\preparar-gcp.ps1
#>
[CmdletBinding()]
param(
    [string] $ArchivoVariables = (Join-Path $PSScriptRoot 'variables.psd1')
)

. "$PSScriptRoot\comun.ps1"
$v = Read-Variables $ArchivoVariables
$p = $v.ProyectoGcp
$r = $v.Region
$sa = "crediexpress-back@$p.iam.gserviceaccount.com"

# Ejecuta $Crear solo si $Existe falla (el recurso no existe todavía).
function Confirm-Recurso([string] $Nombre, [scriptblock] $Existe, [scriptblock] $Crear) {
    if (Test-Comando $Existe) { Write-Host "    (ya existe) $Nombre" -ForegroundColor DarkGray; return }
    Invoke-Paso "Crear $Nombre" $Crear
}

Invoke-Paso 'Seleccionar proyecto' { gcloud config set project $p }

Invoke-Paso 'Habilitar APIs' {
    gcloud services enable container.googleapis.com artifactregistry.googleapis.com `
        sqladmin.googleapis.com secretmanager.googleapis.com compute.googleapis.com `
        servicenetworking.googleapis.com identitytoolkit.googleapis.com
}

Confirm-Recurso 'Artifact Registry crediexpress' `
    { gcloud artifacts repositories describe crediexpress --location=$r } `
    { gcloud artifacts repositories create crediexpress --repository-format=docker --location=$r }

# --- Red privada para Cloud SQL ---
Confirm-Recurso 'rango privado google-managed-services' `
    { gcloud compute addresses describe google-managed-services --global } `
    { gcloud compute addresses create google-managed-services --global --purpose=VPC_PEERING --prefix-length=16 --network=default }

Confirm-Recurso 'conexión VPC con los servicios de Google' `
    {
        $conexiones = gcloud services vpc-peerings list --network=default --service=servicenetworking.googleapis.com --format='value(peering)'
        if (-not $conexiones) { cmd /c exit 1 }
    } `
    { gcloud services vpc-peerings connect --service=servicenetworking.googleapis.com --ranges=google-managed-services --network=default }

# --- Clúster ---
Confirm-Recurso "clúster GKE Autopilot $($v.Cluster) (tarda ~10 min)" `
    { gcloud container clusters describe $v.Cluster --region=$r } `
    { gcloud container clusters create-auto $v.Cluster --region=$r }

Invoke-Paso 'Activar add-on de Secret Manager en el clúster' {
    gcloud container clusters update $v.Cluster --region=$r --enable-secret-manager
}

# --- Cloud SQL ---
Confirm-Recurso "Cloud SQL $($v.InstanciaSql) (tarda ~10 min)" `
    { gcloud sql instances describe $v.InstanciaSql } `
    {
        gcloud sql instances create $v.InstanciaSql --database-version=MYSQL_8_0 --region=$r `
            --tier=db-f1-micro --network="projects/$p/global/networks/default" --no-assign-ip `
            --backup --enable-bin-log --storage-auto-increase
    }

Confirm-Recurso "base de datos $($v.BaseDatos)" `
    { gcloud sql databases describe $v.BaseDatos --instance=$v.InstanciaSql } `
    { gcloud sql databases create $v.BaseDatos --instance=$v.InstanciaSql --charset=utf8mb4 }

# La contraseña se genera aquí, va directo a Cloud SQL y a Secret Manager, y nunca se muestra.
if (-not (Test-Comando { gcloud secrets describe db-password })) {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $password = [Convert]::ToBase64String($bytes) -replace '[+/=]', ''
    $temporal = [System.IO.Path]::GetTempFileName()
    try {
        # Sin salto de línea al final: el archivo es exactamente la contraseña.
        [System.IO.File]::WriteAllText($temporal, $password)
        Invoke-Paso "Crear usuario $($v.UsuarioBd) en Cloud SQL" {
            gcloud sql users create $v.UsuarioBd --instance=$v.InstanciaSql --host='%' --password=$password
        }
        Invoke-Paso 'Guardar contraseña en Secret Manager (db-password)' {
            gcloud secrets create db-password --replication-policy=automatic --data-file=$temporal
        }
    }
    finally {
        Remove-Item $temporal -Force
        $password = $null
    }
}
else {
    Write-Host '    (ya existe) secreto db-password' -ForegroundColor DarkGray
}

# --- Identidad del back (Workload Identity) ---
Confirm-Recurso 'cuenta de servicio crediexpress-back' `
    { gcloud iam service-accounts describe $sa } `
    { gcloud iam service-accounts create crediexpress-back --display-name='Credi Express back' }

Invoke-Paso 'Permiso: conectarse a Cloud SQL' {
    gcloud projects add-iam-policy-binding $p --member="serviceAccount:$sa" --role=roles/cloudsql.client --condition=None | Out-Null
}
Invoke-Paso 'Permiso: leer solo el secreto db-password' {
    gcloud secrets add-iam-policy-binding db-password --member="serviceAccount:$sa" --role=roles/secretmanager.secretAccessor | Out-Null
}
Invoke-Paso 'Enlazar la cuenta de Kubernetes con la de GCP' {
    gcloud iam service-accounts add-iam-policy-binding $sa --role=roles/iam.workloadIdentityUser `
        --member="serviceAccount:$p.svc.id.goog[$($v.Namespace)/crediexpress-back]" | Out-Null
}

# --- Cloud Armor, TLS e IP ---
Confirm-Recurso 'política Cloud Armor crediexpress-armor' `
    { gcloud compute security-policies describe crediexpress-armor } `
    {
        gcloud compute security-policies create crediexpress-armor --description='Credi Express'
        if ($LASTEXITCODE -ne 0) { return }
        # Máximo 100 peticiones por minuto por IP.
        gcloud compute security-policies rules create 1000 --security-policy=crediexpress-armor `
            --expression='true' --action=throttle --rate-limit-threshold-count=100 `
            --rate-limit-threshold-interval-sec=60 --conform-action=allow --exceed-action=deny-429 --enforce-on-key=IP
        if ($LASTEXITCODE -ne 0) { return }
        # Reglas administradas OWASP: inyección SQL y XSS.
        gcloud compute security-policies rules create 900 --security-policy=crediexpress-armor `
            --expression="evaluatePreconfiguredWaf('sqli-v33-stable') || evaluatePreconfiguredWaf('xss-v33-stable')" `
            --action=deny-403
    }

Confirm-Recurso 'política SSL crediexpress-ssl (TLS 1.2+)' `
    { gcloud compute ssl-policies describe crediexpress-ssl --global } `
    { gcloud compute ssl-policies create crediexpress-ssl --profile=MODERN --min-tls-version=1.2 --global }

Confirm-Recurso 'IP fija crediexpress-ip' `
    { gcloud compute addresses describe crediexpress-ip --global } `
    { gcloud compute addresses create crediexpress-ip --global }

$ip = gcloud compute addresses describe crediexpress-ip --global --format='value(address)'
Write-Host ''
Write-Host 'Infraestructura lista.' -ForegroundColor Green
Write-Host "Siguiente: en tu proveedor de dominio crea un registro A  $($v.Dominio) -> $ip" -ForegroundColor Yellow
Write-Host "y agrega $($v.Dominio) en Firebase > Authentication > Configuracion > Dominios autorizados." -ForegroundColor Yellow

<#
.SYNOPSIS
    Crea la infraestructura de GCP de Credi Express. Se corre UNA vez (si algo ya existe, lo salta).

.DESCRIPTION
    Arquitectura de costo cero (docs/DESPLIEGUE-GCP.md):
      Firebase Hosting (front) --/api/**--> Cloud Run (back) --IP interna--> VM e2-micro con MySQL 8

    Crea: APIs, Artifact Registry (con limpieza de imágenes viejas), Private Google Access en la
    subred, IP interna fija para la VM, reglas de firewall, contraseña de la base en Secret Manager
    (generada aquí, nunca se muestra), cuentas de servicio, bucket de respaldos y la VM.

    La VM se crea con una IP externa TEMPORAL solo para instalar MySQL; al terminar se le quita
    (sin IP externa no hay costo de IP ni puerta de entrada desde internet).

    Requiere gcloud con sesión iniciada (gcloud auth login), permisos de Owner en el proyecto y la
    facturación activa (plan Blaze en Firebase); el uso se mantiene dentro de la capa gratuita.

.EXAMPLE
    .\deploy\preparar-gcp.ps1
#>
[CmdletBinding()]
param(
    [string] $ArchivoVariables = (Join-Path $PSScriptRoot 'variables.psd1')
)

. "$PSScriptRoot\comun.ps1"
$v = Read-Variables $ArchivoVariables
$n = Get-Nombres $v
$p = $v.ProyectoGcp
$r = $v.Region
$z = $v.Zona

# Ejecuta $Crear solo si $Existe falla (el recurso no existe todavía).
function Confirm-Recurso([string] $Nombre, [scriptblock] $Existe, [scriptblock] $Crear) {
    if (Test-Comando $Existe) { Write-Host "    (ya existe) $Nombre" -ForegroundColor DarkGray; return }
    Invoke-Paso "Crear $Nombre" $Crear
}

# Archivo temporal UTF-8 sin BOM (gcloud no acepta el BOM en JSON).
function New-ArchivoTemporal([string] $Contenido) {
    $archivo = [System.IO.Path]::GetTempFileName()
    [System.IO.File]::WriteAllText($archivo, $Contenido)
    return $archivo
}

Invoke-Paso 'Seleccionar proyecto' { gcloud config set project $p }

$facturacion = gcloud billing projects describe $p --format='value(billingEnabled)'
if ($facturacion -ne 'True') {
    throw "El proyecto $p no tiene facturación activa. Cámbialo al plan Blaze (Consola de Firebase > Uso y facturación) y vuelve a correr el script."
}

Invoke-Paso 'Habilitar APIs' {
    gcloud services enable run.googleapis.com artifactregistry.googleapis.com compute.googleapis.com `
        secretmanager.googleapis.com iap.googleapis.com storage.googleapis.com `
        identitytoolkit.googleapis.com firebasehosting.googleapis.com
}

# --- Artifact Registry: solo 0.5 GB gratis, así que se conservan las 2 imágenes más recientes ---
Confirm-Recurso 'Artifact Registry crediexpress' `
    { gcloud artifacts repositories describe crediexpress --location=$r } `
    { gcloud artifacts repositories create crediexpress --repository-format=docker --location=$r }

$politica = New-ArchivoTemporal @'
[
  { "name": "conservar-recientes", "action": { "type": "Keep" }, "mostRecentVersions": { "keepCount": 2 } },
  { "name": "borrar-el-resto", "action": { "type": "Delete" }, "condition": { "tagState": "ANY" } }
]
'@
try {
    Invoke-Paso 'Limpieza automática de imágenes viejas' {
        gcloud artifacts repositories set-cleanup-policies crediexpress --location=$r --policy=$politica --no-dry-run | Out-Null
    }
}
finally { Remove-Item $politica -Force }

# --- Red: la VM no tendrá IP externa; llega a Secret Manager y Cloud Storage por Private Google Access ---
Invoke-Paso 'Private Google Access en la subred default' {
    gcloud compute networks subnets update default --region=$r --enable-private-ip-google-access
}
$rangoSubred = gcloud compute networks subnets describe default --region=$r --format='value(ipCidrRange)'
if (-not $rangoSubred) { throw "No se encontró la subred default en $r." }

Confirm-Recurso "IP interna fija $($n.IpVm)" `
    { gcloud compute addresses describe $n.IpVm --region=$r } `
    { gcloud compute addresses create $n.IpVm --region=$r --subnet=default }
$ipVm = gcloud compute addresses describe $n.IpVm --region=$r --format='value(address)'

# Cloud Run (Direct VPC egress) toma IPs de la subred; solo eso y el túnel de IAP llegan a MySQL.
Confirm-Recurso 'firewall crediexpress-mysql-desde-back' `
    { gcloud compute firewall-rules describe crediexpress-mysql-desde-back } `
    {
        gcloud compute firewall-rules create crediexpress-mysql-desde-back --network=default `
            --direction=INGRESS --action=allow --rules=tcp:3306 `
            --source-ranges=$rangoSubred --target-tags=$($n.TagVm)
    }
# 35.235.240.0/20 es el rango fijo de IAP: SSH y MySQL solo por túnel autenticado con tu cuenta.
Confirm-Recurso 'firewall crediexpress-mysql-desde-iap' `
    { gcloud compute firewall-rules describe crediexpress-mysql-desde-iap } `
    {
        gcloud compute firewall-rules create crediexpress-mysql-desde-iap --network=default `
            --direction=INGRESS --action=allow --rules='tcp:22,tcp:3306' `
            --source-ranges=35.235.240.0/20 --target-tags=$($n.TagVm)
    }

# --- Contraseña de la base: se genera aquí, va directo a Secret Manager y nunca se muestra ---
if (-not (Test-Comando { gcloud secrets describe db-password })) {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    # Solo letras y números: la VM la usa dentro de una sentencia SQL.
    $password = [Convert]::ToBase64String($bytes) -replace '[+/=]', ''
    $temporal = New-ArchivoTemporal $password
    try {
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

# --- Cuentas de servicio: cada una con lo mínimo ---
Confirm-Recurso 'cuenta de servicio crediexpress-back' `
    { gcloud iam service-accounts describe $n.SaBack } `
    { gcloud iam service-accounts create crediexpress-back --display-name='Credi Express back (Cloud Run)' }
Confirm-Recurso 'cuenta de servicio crediexpress-vm' `
    { gcloud iam service-accounts describe $n.SaVm } `
    { gcloud iam service-accounts create crediexpress-vm --display-name='Credi Express VM de MySQL' }

foreach ($sa in @($n.SaBack, $n.SaVm)) {
    Invoke-Paso "Permiso: $sa lee solo el secreto db-password" {
        gcloud secrets add-iam-policy-binding db-password --member="serviceAccount:$sa" `
            --role=roles/secretmanager.secretAccessor | Out-Null
    }
}
Invoke-Paso 'Permiso: la VM escribe logs' {
    gcloud projects add-iam-policy-binding $p --member="serviceAccount:$($n.SaVm)" `
        --role=roles/logging.logWriter --condition=None | Out-Null
}

# --- Bucket de respaldos: privado, en la región gratuita, borra lo de más de 30 días ---
$bucket = "gs://$($v.BucketRespaldos)"
Confirm-Recurso "bucket $bucket" `
    { gcloud storage buckets describe $bucket } `
    {
        gcloud storage buckets create $bucket --location=$r --default-storage-class=STANDARD `
            --uniform-bucket-level-access --public-access-prevention
    }
$ciclo = New-ArchivoTemporal '{ "rule": [ { "action": { "type": "Delete" }, "condition": { "age": 30 } } ] }'
try {
    Invoke-Paso 'Borrar respaldos de más de 30 días' { gcloud storage buckets update $bucket --lifecycle-file=$ciclo }
}
finally { Remove-Item $ciclo -Force }
# objectCreator: puede subir respaldos pero no leerlos, borrarlos ni sobrescribirlos.
Invoke-Paso 'Permiso: la VM sube respaldos al bucket' {
    gcloud storage buckets add-iam-policy-binding $bucket --member="serviceAccount:$($n.SaVm)" `
        --role=roles/storage.objectCreator | Out-Null
}

# --- VM e2-micro (capa gratuita: e2-micro + 30 GB de disco ESTÁNDAR en us-west1/us-central1/us-east1) ---
$script = Join-Path $PSScriptRoot 'vm\instalar-mysql.sh'
Confirm-Recurso "VM $($v.Vm)" `
    { gcloud compute instances describe $v.Vm --zone=$z } `
    {
        gcloud compute instances create $v.Vm --zone=$z --machine-type=e2-micro `
            --image-family=ubuntu-2404-lts-amd64 --image-project=ubuntu-os-cloud `
            --boot-disk-size=30GB --boot-disk-type=pd-standard `
            --subnet=default --private-network-ip=$ipVm `
            --service-account=$($n.SaVm) --scopes=cloud-platform `
            --tags=$($n.TagVm) `
            --shielded-secure-boot --shielded-vtpm --shielded-integrity-monitoring `
            --deletion-protection `
            --metadata="enable-guest-attributes=TRUE,enable-oslogin=TRUE,base-datos=$($v.BaseDatos),usuario-bd=$($v.UsuarioBd),bucket-respaldos=$($v.BucketRespaldos)" `
            --metadata-from-file="startup-script=$script"
    }

# El script de la VM publica su avance; MySQL tarda ~5-10 min en instalarse en una e2-micro.
Write-Host '==> Esperando la instalación de MySQL en la VM (hasta 20 min)' -ForegroundColor Cyan
$limite = (Get-Date).AddMinutes(20)
do {
    Start-Sleep -Seconds 20
    $estado = Get-EstadoVm $v
    Write-Host "    estado: $(if ($estado) { $estado } else { 'arrancando' })" -ForegroundColor DarkGray
    if ($estado -like 'error*') {
        throw "La instalación falló ($estado). Revisa: gcloud compute instances get-serial-port-output $($v.Vm) --zone=$z"
    }
} while ($estado -ne 'listo' -and (Get-Date) -lt $limite)
if ($estado -ne 'listo') {
    throw "La instalación no terminó a tiempo. Revisa: gcloud compute instances get-serial-port-output $($v.Vm) --zone=$z"
}

# Ya instalado: se quita la IP externa temporal.
$accesoExterno = gcloud compute instances describe $v.Vm --zone=$z --format='value(networkInterfaces[0].accessConfigs[0].name)'
if ($accesoExterno) {
    Invoke-Paso 'Quitar la IP externa temporal de la VM' {
        gcloud compute instances delete-access-config $v.Vm --zone=$z --access-config-name=$accesoExterno
    }
}

Write-Host ''
Write-Host 'Infraestructura lista.' -ForegroundColor Green
Write-Host "MySQL en $ipVm (solo red interna). Siguiente: .\deploy\migrar-bd.ps1 (copia la base local a la VM)" -ForegroundColor Yellow
Write-Host 'Recomendado: alerta de presupuesto de 1 USD en Consola de GCP > Facturación > Presupuestos y alertas.' -ForegroundColor Yellow

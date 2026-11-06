<#
.SYNOPSIS
    Despliegue manual del back de Credi Express en Cloud Run (docs/DESPLIEGUE-GCP.md).

.DESCRIPTION
    1. Pruebas y build en tu equipo (./gradlew clean build -PsinBd: sin contextLoads, que usaría tu
       MySQL local). Si fallan, NO se despliega.
    2. Cloud Build arma la imagen en la nube con el Dockerfile (con CDS para arrancar más rápido) y la
       sube a Artifact Registry. No necesitas Docker. Solo se sube lo que permite .gcloudignore
       (Dockerfile, gradle y src): nunca config/ ni deploy/.
    3. gcloud run deploy. Cloud Run solo manda tráfico a la revisión nueva si arranca bien
       (Flyway aplica las migraciones pendientes al arrancar); si falla, sigue la anterior.
    4. Prueba /api/actuator/health en la URL del servicio.

    Costo: min-instances=0 (no cobra mientras nadie la usa) y max-instances=2 (tope de gasto).
    Cloud Build: 2,500 minutos gratis al mes (cada despliegue usa ~5-10). El código se sube a un
    bucket propio en la región gratuita que borra lo de más de 1 día.

.EXAMPLE
    .\deploy\deploy-back.ps1 -Version 0.1.0
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidatePattern('^\d+\.\d+\.\d+$')] [string] $Version,
    [string] $ArchivoVariables = (Join-Path $PSScriptRoot 'variables.psd1'),
    [string] $JavaHome = $env:JAVA_HOME
)

. "$PSScriptRoot\comun.ps1"
$v = Read-Variables $ArchivoVariables
$n = Get-Nombres $v
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

$imagen = "$($n.Repositorio)/back:$Version"

# Gradle necesita Java 17+ para ejecutarse (el proyecto compila con Java 21).
if (-not $JavaHome -or -not (Select-String -Path (Join-Path $JavaHome 'release') -Pattern 'JAVA_VERSION="21' -Quiet)) {
    throw "JAVA_HOME debe apuntar a un JDK 21 (actual: '$JavaHome'). Ejemplo: -JavaHome 'C:\Program Files\Java\jdk-21'"
}
$env:JAVA_HOME = $JavaHome

if (git tag --list "back-v$Version") { throw "La versión $Version ya existe (tag back-v$Version). Usa una versión nueva." }

# Lista blanca del back: UIDs de Firebase en deploy/usuarios-permitidos.txt (ignorado por git).
$archivoUsuarios = Join-Path $PSScriptRoot 'usuarios-permitidos.txt'
if (-not (Test-Path $archivoUsuarios)) { throw "Falta $archivoUsuarios (un UID de Firebase por línea). Sin él nadie podría entrar." }
$usuariosPermitidos = @(Get-Content $archivoUsuarios | ForEach-Object { $_.Trim() } | Where-Object { $_ -and -not $_.StartsWith('#') })
if ($usuariosPermitidos.Count -eq 0) { throw "$archivoUsuarios no tiene UIDs." }
$invalidos = @($usuariosPermitidos | Where-Object { $_ -notmatch '^[A-Za-z0-9]{20,128}$' })
if ($invalidos.Count -gt 0) { throw "UIDs con formato raro en ${archivoUsuarios}: $($invalidos -join ', ')" }

$ipVm = gcloud compute addresses describe $n.IpVm --region=$($v.Region) --project=$($v.ProyectoGcp) --format='value(address)'
if (-not $ipVm) { throw "No existe la IP $($n.IpVm). Corre primero .\deploy\preparar-gcp.ps1" }

Invoke-Paso 'Pruebas y build' { .\gradlew.bat clean build -PsinBd }

# --- Cloud Build (la primera vez lo prepara; después solo lo confirma) ---
$p = $v.ProyectoGcp
$r = $v.Region
Invoke-Paso 'Habilitar Cloud Build' { gcloud services enable cloudbuild.googleapis.com --project=$p }
# Bucket propio en la región gratuita para subir el código: el de Cloud Build por omisión es
# multirregional y no entra en la capa gratuita de Cloud Storage.
$bucketFuentes = "gs://$p-cloudbuild-fuentes"
if (-not (Test-Comando { gcloud storage buckets describe $bucketFuentes --project=$p })) {
    Invoke-Paso "Crear bucket $bucketFuentes" {
        gcloud storage buckets create $bucketFuentes --project=$p --location=$r --default-storage-class=STANDARD `
            --uniform-bucket-level-access --public-access-prevention
    }
    $ciclo = [IO.Path]::GetTempFileName()
    [IO.File]::WriteAllText($ciclo, '{ "rule": [ { "action": { "type": "Delete" }, "condition": { "age": 1 } } ] }')
    try { Invoke-Paso 'Borrar el código subido de más de 1 día' { gcloud storage buckets update $bucketFuentes --lifecycle-file=$ciclo } }
    finally { Remove-Item $ciclo -Force }
}
# Permisos mínimos de la cuenta con la que corre Cloud Build: leer el código, subir la imagen y
# escribir logs. Repetirlos no cambia nada.
# Recién habilitada la API puede tardar un par de minutos en responder: se reintenta.
$saBuild = ''
for ($intento = 1; $intento -le 12 -and -not $saBuild; $intento++) {
    $anterior = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $saBuild = [string] (gcloud builds get-default-service-account --project=$p --format='value(serviceAccountEmail)' 2>$null) }
    finally { $ErrorActionPreference = $anterior }
    $saBuild = $saBuild.Trim() -replace '^.*/', ''
    if (-not $saBuild) { Write-Host '    Esperando a que Cloud Build esté listo...' -ForegroundColor DarkGray; Start-Sleep -Seconds 15 }
}
if (-not $saBuild) { throw 'No se pudo obtener la cuenta de servicio de Cloud Build. Espera unos minutos y vuelve a correr el script.' }
Invoke-Paso "Permiso: Cloud Build lee el código ($saBuild)" {
    gcloud storage buckets add-iam-policy-binding $bucketFuentes --member="serviceAccount:$saBuild" `
        --role=roles/storage.objectViewer | Out-Null
}
Invoke-Paso 'Permiso: Cloud Build sube imágenes' {
    gcloud artifacts repositories add-iam-policy-binding crediexpress --project=$p --location=$r `
        --member="serviceAccount:$saBuild" --role=roles/artifactregistry.writer | Out-Null
}
Invoke-Paso 'Permiso: Cloud Build escribe logs' {
    gcloud projects add-iam-policy-binding $p --member="serviceAccount:$saBuild" `
        --role=roles/logging.logWriter --condition=None | Out-Null
}

# Solo se sube lo que permite .gcloudignore (Dockerfile, gradle y src).
Invoke-Paso 'Construir y publicar imagen con Cloud Build' {
    gcloud builds submit $raiz --project=$p --region=$r --tag=$imagen --timeout=30m `
        --gcs-source-staging-dir="$bucketFuentes/fuentes"
}

$variablesEntorno = [ordered]@{
    SPRING_PROFILES_ACTIVE = 'cloudrun'
    # Misma zona que el MySQL de la VM (-06:00); sin esto la JVM corre en UTC y las columnas
    # TIMESTAMP (cliente.fecha_alta, calculadora_prestamos.fecha) se leerían 6 horas corridas.
    TZ                     = 'America/Mexico_City'
    FIREBASE_PROJECT_ID    = $v.FirebaseProjectId
    DB_URL                 = "jdbc:mysql://${ipVm}:3306/$($v.BaseDatos)?sslMode=REQUIRED"
    DB_USERNAME            = $v.UsuarioBd
    USUARIOS_PERMITIDOS    = $usuariosPermitidos -join ','
}
# Se pasan en un archivo YAML: con --set-env-vars la coma de la lista de UIDs se tomaría como
# separador de variables.
$archivoEntorno = [IO.Path]::GetTempFileName()
[IO.File]::WriteAllText($archivoEntorno, (($variablesEntorno.GetEnumerator() | ForEach-Object { "$($_.Key): '$($_.Value)'" }) -join "`n"))

try {
    Invoke-Paso 'Desplegar en Cloud Run' {
        # --allow-unauthenticated: la llamada llega de Firebase Hosting; quien valida es Spring Security
        #   (todo endpoint, salvo el health, exige el ID token de Firebase de un usuario de la lista).
        # --network/--subnet: Direct VPC egress, sin conector (el conector cobra aunque no se use).
        # --vpc-egress=private-ranges-only: solo el tráfico a IPs privadas (MySQL) va por la VPC; las
        #   llaves públicas de Firebase se descargan por internet como siempre.
        # --cpu-boost: más CPU solo durante el arranque.
        gcloud run deploy $v.ServicioBack --project=$($v.ProyectoGcp) --region=$($v.Region) `
            --image=$imagen --service-account=$($n.SaBack) `
            --allow-unauthenticated --ingress=all --port=8080 `
            --network=default --subnet=default --vpc-egress=private-ranges-only `
            --cpu=1 --memory=1Gi --concurrency=20 --timeout=60 `
            --min-instances=0 --max-instances=2 --cpu-boost `
            --env-vars-file=$archivoEntorno `
            --set-secrets='/etc/secrets/db-password=db-password:latest' `
            --labels="app=crediexpress,version=$($Version -replace '\.', '-')"
    }
}
finally { Remove-Item $archivoEntorno -Force }

$url = gcloud run services describe $v.ServicioBack --project=$($v.ProyectoGcp) --region=$($v.Region) --format='value(status.url)'
Write-Host "==> Probando $url/api/actuator/health" -ForegroundColor Cyan
$salud = Invoke-RestMethod -Uri "$url/api/actuator/health" -TimeoutSec 90
if ($salud.status -ne 'UP') { throw "El back respondió '$($salud.status)'. Revisa los logs en Consola > Cloud Run > $($v.ServicioBack)." }

git tag "back-v$Version"
Write-Host "Back $Version desplegado. Recuerda: git push origin back-v$Version" -ForegroundColor Green

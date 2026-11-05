<#
.SYNOPSIS
    Despliegue manual del back de Credi Express en Cloud Run (docs/DESPLIEGUE-GCP.md).

.DESCRIPTION
    1. Pruebas y build (./gradlew clean build). Si fallan, NO se despliega.
    2. Imagen versionada (con CDS para arrancar más rápido) -> Artifact Registry.
    3. gcloud run deploy. Cloud Run solo manda tráfico a la revisión nueva si arranca bien
       (Flyway aplica las migraciones pendientes al arrancar); si falla, sigue la anterior.
    4. Prueba /api/actuator/health en la URL del servicio.

    Costo: min-instances=0 (no cobra mientras nadie la usa) y max-instances=2 (tope de gasto).

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

$ipVm = gcloud compute addresses describe $n.IpVm --region=$($v.Region) --project=$($v.ProyectoGcp) --format='value(address)'
if (-not $ipVm) { throw "No existe la IP $($n.IpVm). Corre primero .\deploy\preparar-gcp.ps1" }

Invoke-Paso 'Pruebas y build' { .\gradlew.bat clean build }
Invoke-Paso 'Construir imagen' { docker build -t $imagen . }
Invoke-Paso 'Autorizar Docker con Artifact Registry' { gcloud auth configure-docker "$($v.Region)-docker.pkg.dev" --quiet }
Invoke-Paso 'Publicar imagen' { docker push $imagen }

$variablesEntorno = @(
    'SPRING_PROFILES_ACTIVE=cloudrun'
    # Misma zona que el MySQL de la VM (-06:00); sin esto la JVM corre en UTC y las columnas
    # TIMESTAMP (cliente.fecha_alta, calculadora_prestamos.fecha) se leerían 6 horas corridas.
    'TZ=America/Mexico_City'
    "FIREBASE_PROJECT_ID=$($v.FirebaseProjectId)"
    "DB_URL=jdbc:mysql://${ipVm}:3306/$($v.BaseDatos)?sslMode=REQUIRED"
    "DB_USERNAME=$($v.UsuarioBd)"
) -join ','

Invoke-Paso 'Desplegar en Cloud Run' {
    # --allow-unauthenticated: la llamada llega de Firebase Hosting; quien valida es Spring Security
    #   (todo endpoint, salvo el health, exige el ID token de Firebase).
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
        --set-env-vars=$variablesEntorno `
        --set-secrets='/etc/secrets/db-password=db-password:latest' `
        --labels="app=crediexpress,version=$($Version -replace '\.', '-')"
}

$url = gcloud run services describe $v.ServicioBack --project=$($v.ProyectoGcp) --region=$($v.Region) --format='value(status.url)'
Write-Host "==> Probando $url/api/actuator/health" -ForegroundColor Cyan
$salud = Invoke-RestMethod -Uri "$url/api/actuator/health" -TimeoutSec 90
if ($salud.status -ne 'UP') { throw "El back respondió '$($salud.status)'. Revisa los logs en Consola > Cloud Run > $($v.ServicioBack)." }

git tag "back-v$Version"
Write-Host "Back $Version desplegado. Recuerda: git push origin back-v$Version" -ForegroundColor Green

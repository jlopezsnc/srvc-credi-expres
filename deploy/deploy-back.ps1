<#
.SYNOPSIS
    Despliegue manual del back de Credi Express en GKE (docs §3.4).

.DESCRIPTION
    1. Pruebas y build (./gradlew clean build). Si fallan, NO se despliega.
    2. Imagen versionada -> Artifact Registry.
    3. kubectl apply de k8s/ (con las variables de variables.psd1) y espera del rollout.
       Si el rollout falla, regresa a la versión anterior.

.EXAMPLE
    .\deploy\deploy-back.ps1 -Version 0.1.0

.EXAMPLE
    # Solo genera los manifiestos finales en build/k8s para revisarlos, sin desplegar nada.
    .\deploy\deploy-back.ps1 -Version 0.1.0 -SoloManifiestos
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidatePattern('^\d+\.\d+\.\d+$')] [string] $Version,
    [string] $ArchivoVariables = (Join-Path $PSScriptRoot 'variables.psd1'),
    [string] $JavaHome = $env:JAVA_HOME,
    [switch] $SoloManifiestos
)

. "$PSScriptRoot\comun.ps1"
$v = Read-Variables $ArchivoVariables
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

$imagen = "$($v.Region)-docker.pkg.dev/$($v.ProyectoGcp)/crediexpress/back:$Version"
$valores = @{
    PROYECTO_GCP        = $v.ProyectoGcp
    FIREBASE_PROJECT_ID = $v.FirebaseProjectId
    DOMINIO             = $v.Dominio
    BASE_DATOS          = $v.BaseDatos
    USUARIO_BD          = $v.UsuarioBd
    CLOUDSQL_CONEXION   = "$($v.ProyectoGcp):$($v.Region):$($v.InstanciaSql)"
    IMAGEN              = $imagen
}

# Orden: identidad y configuración primero, luego la app y al final el Ingress.
$archivos = @(
    'k8s/serviceaccount.yaml', 'k8s/configmap.yaml', 'k8s/secretproviderclass.yaml',
    'k8s/backendconfig.yaml', 'k8s/service.yaml', 'k8s/networkpolicy.yaml', 'k8s/deployment.yaml',
    'k8s/ingress/frontendconfig.yaml', 'k8s/ingress/managedcertificate.yaml', 'k8s/ingress/ingress.yaml'
)
$manifiestos = ($archivos | ForEach-Object { Convert-Manifiesto (Get-Content $_ -Raw) $valores }) -join "`n---`n"

$salida = Join-Path $raiz 'build/k8s'
New-Item -ItemType Directory -Force $salida | Out-Null
$archivoFinal = Join-Path $salida "back-$Version.yaml"
[System.IO.File]::WriteAllText($archivoFinal, $manifiestos)
Write-Host "Manifiestos generados en $archivoFinal" -ForegroundColor DarkGray
if ($SoloManifiestos) { return }

# Gradle necesita Java 17+ para ejecutarse (el proyecto compila con Java 21).
if (-not $JavaHome -or -not (Select-String -Path (Join-Path $JavaHome 'release') -Pattern 'JAVA_VERSION="21' -Quiet)) {
    throw "JAVA_HOME debe apuntar a un JDK 21 (actual: '$JavaHome'). Ejemplo: -JavaHome 'C:\Program Files\Java\jdk-21'"
}
$env:JAVA_HOME = $JavaHome

if (git tag --list "back-v$Version") { throw "La versión $Version ya existe (tag back-v$Version). Usa una versión nueva." }

Invoke-Paso 'Pruebas y build' { .\gradlew.bat clean build }
Invoke-Paso 'Construir imagen' { docker build -t $imagen . }
Invoke-Paso 'Autorizar Docker con Artifact Registry' { gcloud auth configure-docker "$($v.Region)-docker.pkg.dev" --quiet }
Invoke-Paso 'Publicar imagen' { docker push $imagen }
Invoke-Paso 'Obtener credenciales del clúster' {
    gcloud container clusters get-credentials $v.Cluster --region $v.Region --project $v.ProyectoGcp
}
# El clúster valida todos los manifiestos (incluidos los recursos propios de GKE) sin aplicar nada.
Invoke-Paso 'Validar manifiestos en el clúster' { kubectl apply -n $v.Namespace --dry-run=server -f $archivoFinal }
Invoke-Paso 'Aplicar manifiestos' { kubectl apply -n $v.Namespace -f $archivoFinal }

Write-Host '==> Esperando el rollout' -ForegroundColor Cyan
kubectl rollout status deployment/crediexpress-back -n $v.Namespace --timeout=300s
if ($LASTEXITCODE -ne 0) {
    Write-Warning 'El rollout falló. Regresando a la versión anterior.'
    kubectl rollout undo deployment/crediexpress-back -n $v.Namespace
    throw 'Despliegue revertido. Revisa: kubectl logs deployment/crediexpress-back -c back'
}

git tag "back-v$Version"
Write-Host "Back $Version desplegado. Recuerda: git push origin back-v$Version" -ForegroundColor Green

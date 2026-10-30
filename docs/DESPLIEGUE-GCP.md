# Despliegue en GCP (GKE) — Credi Express

Front y back corren en el mismo clúster GKE Autopilot, detrás de un solo Ingress con HTTPS:

```
Internet ──HTTPS──> Ingress (certificado de Google + Cloud Armor)
                     ├── /api/* ──> crediexpress-back (Spring Boot, Java 21) ──> sidecar Cloud SQL Proxy ──> Cloud SQL
                     └── /*     ──> crediexpress-front (nginx)
```

## Requisitos en tu equipo

| Herramienta | Versión | Verificar |
|---|---|---|
| JDK | 21 | `& "C:\Program Files\Java\jdk-21\bin\java" -version` |
| Docker Desktop | 27+ (encendido) | `docker info` |
| Google Cloud CLI | reciente | `gcloud --version` |
| kubectl | 1.33+ | `kubectl version --client` |
| gke-gcloud-auth-plugin | — | `gcloud components install gke-gcloud-auth-plugin` |

`JAVA_HOME` debe apuntar a un JDK 21 al desplegar (o pasar `-JavaHome 'C:\Program Files\Java\jdk-21'`).

## Paso 1. Llenar las variables (una vez)

Editar `deploy/variables.psd1` y reemplazar cada `CAMBIAR`. No son secretos.

## Paso 2. Crear la infraestructura (una vez)

```powershell
gcloud auth login
.\deploy\preparar-gcp.ps1
```

Crea APIs, Artifact Registry, clúster, Cloud SQL con IP privada, la contraseña de la base (generada
y guardada directo en Secret Manager, nunca se muestra), la cuenta de servicio con Workload
Identity, Cloud Armor, la política TLS y la IP fija. Si algo ya existe, lo salta.

Al terminar muestra la IP: crear el registro **A** del dominio hacia esa IP y agregar el dominio en
Firebase > Authentication > Configuración > Dominios autorizados.

## Paso 3. Esquema de la base

Cloud SQL se crea vacío. Mientras no exista Flyway (docs §2.4), cargar el esquema con
Cloud SQL Studio (Consola > SQL > crediexpress-db > Cloud SQL Studio).

## Paso 4. Desplegar

```powershell
.\deploy\deploy-back.ps1 -Version 0.1.0     # en este repo (también aplica el Ingress)
.\deploy\deploy-front.ps1 -Version 0.1.0 -Proyecto <id> -Region <region> -Cluster crediexpress   # en el repo del front
```

Cada script: pruebas → imagen versionada → push → validación en el clúster (`--dry-run=server`) →
apply → espera del rollout (si falla, `rollout undo`). Para revisar los manifiestos sin desplegar:
`.\deploy\deploy-back.ps1 -Version 0.1.0 -SoloManifiestos` (quedan en `build/k8s/`).

El certificado HTTPS tarda hasta 60 minutos en activarse después de que el dominio apunta a la IP:
`kubectl describe managedcertificate crediexpress-cert`.

## Paso 5. Usuarios

No hay roles: cualquier usuario con un token válido de Firebase tiene acceso total (ver
`REVISION-BACK-Y-PROPUESTA-FRONT.md` §1.4). Crea los usuarios a mano en Firebase > Authentication >
Usuarios y desactiva el registro de usuarios para que nadie más pueda crear una cuenta.

## Seguridad aplicada

| Riesgo | Protección |
|---|---|
| Llamadas sin sesión | Spring valida el ID token de Firebase: firma, emisor, audiencia y expiración |
| Usuario ajeno al negocio | Denegar por defecto: todo endpoint exige token. Registro de usuarios desactivado en Firebase |
| Secretos en código o imagen | Secret Manager montado como archivo + Workload Identity (sin llaves JSON) |
| Base expuesta | Cloud SQL sin IP pública; conexión cifrada por el Auth Proxy |
| Abuso / fuerza bruta | Cloud Armor: 100 peticiones/min por IP + reglas OWASP (SQLi, XSS) |
| Tráfico sin cifrar | Certificado administrado, HTTP → HTTPS, TLS 1.2+ |
| Pod comprometido | NetworkPolicy, usuario no root, sistema de archivos de solo lectura, sin capabilities |
| Actuator expuesto | Puerto 8081 interno, fuera del Service y del Ingress; solo `/health` |

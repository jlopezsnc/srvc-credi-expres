# Despliegue en GCP (costo cero) — Credi Express

Sustituye al despliegue en GKE (2026-11-04): para pocos datos y pocas operaciones al mes, todo cabe
en la capa gratuita de Google Cloud y Firebase.

```
Navegador ──HTTPS──> Firebase Hosting (front Angular, CDN y certificado gratis)
                       └── /api/** ──> Cloud Run crediexpress-back (Spring Boot, escala a 0)
                                         │ Direct VPC egress, IP interna, TLS
                                         ▼
                                       VM e2-micro crediexpress-mysql (MySQL 8, sin IP externa)
                                         └── respaldo diario ──> Cloud Storage (30 días)
```

## Capa gratuita que se usa (us-central1)

| Servicio | Gratis al mes | Uso esperado |
|---|---|---|
| Cloud Run | 2 M peticiones, 180 000 vCPU-s, 360 000 GiB-s | ~6 000 peticiones; ~10 000 vCPU-s contando arranques en frío |
| Compute Engine | 1 e2-micro + 30 GB de disco estándar (us-west1/us-central1/us-east1) | 1 VM, 30 GB `pd-standard` |
| Cloud Storage | 5 GB (regiones de EE. UU.) | Respaldos comprimidos de pocos KB |
| Secret Manager | 6 versiones activas, 10 000 accesos | 1 secreto |
| Artifact Registry | 0.5 GB | Se conservan solo las 2 imágenes más recientes |
| Firebase Hosting / Auth | 10 GB de almacenamiento, 360 MB/día de transferencia; Auth con correo gratis | Front de unos MB |

Requiere el plan **Blaze** (facturación activa) porque Cloud Run y Compute Engine lo exigen; las
cuotas gratuitas se siguen aplicando. **Crea una alerta de presupuesto de 1 USD** (Consola de GCP >
Facturación > Presupuestos y alertas) para enterarte si algo sale de la capa gratuita.

Para evitar cargos: la VM debe ser `e2-micro` con disco `pd-standard` de 30 GB en una de las tres
regiones y sin IP externa; Cloud Run sin `min-instances` y sin "CPU siempre asignada".

## Requisitos en tu equipo

| Herramienta | Verificar |
|---|---|
| JDK 21 | `& "C:\Program Files\Java\jdk-21\bin\java" -version` |
| Google Cloud CLI | `gcloud --version` (instalar de cloud.google.com/sdk) |

No se necesita Docker: la imagen del back la arma Cloud Build en la nube (2,500 minutos gratis al mes).

## Paso 1. Variables

Revisar `deploy/variables.psd1` (no son secretos). El proyecto de GCP debe ser el mismo de Firebase.

## Paso 2. Infraestructura (una vez, ~20 min)

```powershell
gcloud auth login
.\deploy\preparar-gcp.ps1
```

Crea APIs, Artifact Registry, firewall, la contraseña de la base (generada y guardada directo en
Secret Manager; nunca se muestra), cuentas de servicio, el bucket de respaldos y la VM. La VM arranca
con una IP externa **temporal**, instala MySQL con `deploy/vm/instalar-mysql.sh` (que lee la
contraseña de Secret Manager) y el script le quita la IP al terminar. Si algo ya existe, lo salta.

## Paso 3. Copiar la base local a la VM (una vez)

Los datos reales (clientes, préstamos, pagos y convenios) ya están en el MySQL local. Deja de
registrar operaciones en local y corre:

```powershell
.\deploy\migrar-bd.ps1
```

Hace `mysqldump` de la base local `prestamos` a un archivo temporal, lo carga en la VM por el túnel
de IAP, compara las filas de cada tabla y borra el archivo (lleva datos personales). Incluye
`flyway_schema_history`, así que el back arranca sin migrar nada. Si la VM ya tiene datos se
detiene; `-Reemplazar` los sustituye por los locales. Desde aquí la base buena es la de la VM.

Zona horaria: el MySQL de la VM usa `-06:00` y Cloud Run `TZ=America/Mexico_City`, igual que el
equipo local donde se capturaron los datos, para que las columnas `TIMESTAMP` se lean igual. Las
columnas `DATETIME` marcadas "En UTC" no dependen de esto.

## Paso 4. Back

```powershell
.\deploy\deploy-back.ps1 -Version 0.1.0 -JavaHome 'C:\Program Files\Java\jdk-21'
```

Pruebas en tu equipo → Cloud Build arma y publica la imagen con CDS → `gcloud run deploy` →
prueba de `/api/actuator/health` (que también
revisa la conexión a MySQL). Si la revisión nueva no arranca, Cloud Run sigue sirviendo la anterior.

## Paso 5. Front

En el repo del front: `.\deploy\deploy-front.ps1 -Version 0.1.0`. Publica en Firebase Hosting, que
envía `/api/**` a este servicio de Cloud Run (mismo origen: sin CORS).

## Acceso a la base

```powershell
.\deploy\tunel-bd.ps1        # MySQL de la VM en localhost:3307 mientras la ventana siga abierta
gcloud secrets versions access latest --secret=db-password   # contraseña para Workbench
```

En Workbench: host `127.0.0.1`, puerto `3307`, usuario `crediexpress_app`, SSL **Require**.

## Paso 6. Usuarios

Sin roles: cualquier usuario con token válido de Firebase tiene acceso total
(`REVISION-BACK-Y-PROPUESTA-FRONT.md` §1.4). Crea los usuarios a mano en Firebase > Authentication y
desactiva el registro para que nadie más pueda crear una cuenta.

## Operación

| Tarea | Cómo |
|---|---|
| Actualizaciones de seguridad de la VM (mensual) | `.\deploy\mantener-vm.ps1` (pone IP temporal, `apt upgrade`, la quita) |
| Ver respaldos | `gcloud storage ls gs://credi-express-app-respaldos-bd` (y 14 días locales en `/var/backups/crediexpress`) |
| Respaldo manual | `gcloud compute ssh crediexpress-mysql --zone=us-central1-a --tunnel-through-iap --command="sudo /usr/local/bin/respaldo-bd.sh"` |
| Restaurar | Descargar el `.sql.gz` y cargarlo por el túnel: `gunzip -c archivo.sql.gz \| mysql -h 127.0.0.1 -P 3307 -u crediexpress_app -p --ssl-mode=REQUIRED` |
| Logs del back | Consola > Cloud Run > crediexpress-back > Registros |

Primer acceso tras un rato sin uso: Cloud Run arranca la app desde cero (unos segundos, menos gracias
a CDS y `--cpu-boost`). Las siguientes peticiones son inmediatas.

## Seguridad aplicada

| Riesgo | Protección |
|---|---|
| Llamadas sin sesión | Spring valida el ID token de Firebase: firma, emisor, audiencia y expiración |
| Usuario ajeno al negocio | Denegar por defecto: todo endpoint exige token. Registro de usuarios desactivado en Firebase |
| Secretos en código o imagen | Secret Manager montado como archivo en Cloud Run y leído por la VM con su cuenta de servicio |
| Base expuesta | VM sin IP externa; el firewall solo deja llegar a la subred (Cloud Run) y al túnel de IAP; usuario con `REQUIRE SSL` |
| Abuso / gasto | `max-instances=2` y alerta de presupuesto; sin Cloud Armor (costaba más que todo lo demás) |
| Tráfico sin cifrar | HTTPS de Firebase Hosting y de Cloud Run; TLS entre Cloud Run y MySQL |
| Pérdida de datos | Respaldo diario a Cloud Storage (30 días) + 14 días en la VM + binlog de 7 días |

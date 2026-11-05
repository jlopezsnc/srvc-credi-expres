#!/bin/bash
# Startup script de la VM crediexpress-mysql (Ubuntu 24.04, e2-micro). Lo instala
# deploy/preparar-gcp.ps1 como metadato "startup-script" y GCE lo corre en CADA arranque:
#   - Primer arranque (la VM tiene IP externa temporal): instala y configura MySQL 8, crea la base,
#     el usuario de la app y el respaldo diario. Al terminar deja la marca /var/lib/crediexpress/instalado.
#   - Arranques siguientes (ya sin IP externa): solo reporta "listo".
# El avance se publica en el guest attribute crediexpress/estado, que lee preparar-gcp.ps1.
#
# Valores que llegan como metadatos de la instancia: base-datos, usuario-bd, bucket-respaldos.
# La contraseña NO llega por metadatos: se lee aquí mismo de Secret Manager (db-password) con la
# cuenta de servicio de la VM, y nunca sale de la VM ni de Secret Manager.
set -euo pipefail

MD=http://metadata.google.internal/computeMetadata/v1
MARCA=/var/lib/crediexpress/instalado

estado() {
  curl -s -X PUT --data "$1" -H 'Metadata-Flavor: Google' \
    "$MD/instance/guest-attributes/crediexpress/estado" > /dev/null || true
}
meta() { curl -sf -H 'Metadata-Flavor: Google' "$MD/instance/attributes/$1"; }

if [ -f "$MARCA" ]; then
  estado listo
  exit 0
fi

trap 'estado "error en la linea $LINENO"' ERR
estado instalando

BASE=$(meta base-datos)
USUARIO=$(meta usuario-bd)

# --- Memoria: 1 GB de RAM es poco para MySQL + apt; 1 GB de swap evita que lo mate el OOM killer.
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 1G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# --- MySQL 8 (paquete de Ubuntu 24.04) ---
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q mysql-server
apt-get upgrade -y -q

cat > /etc/mysql/mysql.conf.d/zz-crediexpress.cnf <<'EOF'
# Configuración de Credi Express (deploy/vm/instalar-mysql.sh). Ajustada a una e2-micro (1 GB RAM).
[mysqld]
# Escucha en la IP interna; el firewall solo deja entrar a Cloud Run (subred) y al túnel de IAP.
bind-address             = 0.0.0.0
skip-name-resolve
# Toda conexión por red debe ir cifrada (MySQL genera sus certificados al instalarse).
require_secure_transport = ON
# Hora de México (sin horario de verano desde 2022), igual que el MySQL local donde se capturaron
# los datos: las columnas TIMESTAMP y CURRENT_TIMESTAMP se leen igual que antes. Las columnas
# DATETIME "En UTC" no dependen de esto (Hibernate las escribe en UTC). Va junto con TZ en Cloud Run.
default-time-zone        = '-06:00'
character-set-server     = utf8mb4
collation-server         = utf8mb4_0900_ai_ci
# Memoria
innodb_buffer_pool_size  = 128M
performance_schema       = OFF
max_connections          = 30
# Binlog 7 días (recuperación entre respaldos); la carga del Excel usa un procedimiento temporal.
binlog_expire_logs_seconds      = 604800
log_bin_trust_function_creators = 1
EOF
systemctl restart mysql

# --- Contraseña desde Secret Manager (API REST con el token de la cuenta de servicio de la VM) ---
TOKEN=$(curl -sf -H 'Metadata-Flavor: Google' "$MD/instance/service-accounts/default/token" |
  python3 -c 'import sys, json; print(json.load(sys.stdin)["access_token"])')
PROYECTO=$(curl -sf -H 'Metadata-Flavor: Google' "$MD/project/project-id")
PASSWORD=$(curl -sf -H "Authorization: Bearer $TOKEN" \
  "https://secretmanager.googleapis.com/v1/projects/$PROYECTO/secrets/db-password/versions/latest:access" |
  python3 -c 'import sys, json, base64; print(base64.b64decode(json.load(sys.stdin)["payload"]["data"]).decode(), end="")')

# --- Base y usuario de la app (root solo entra por el socket local, sin contraseña de red) ---
# '%': el firewall ya limita quién llega al puerto (Cloud Run y el túnel de IAP).
# ALL en la base: Flyway crea y modifica tablas al arrancar el back.
mysql <<SQL
CREATE DATABASE IF NOT EXISTS \`$BASE\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$USUARIO'@'%' IDENTIFIED BY '$PASSWORD' REQUIRE SSL;
ALTER USER '$USUARIO'@'%' IDENTIFIED BY '$PASSWORD' REQUIRE SSL;
GRANT ALL PRIVILEGES ON \`$BASE\`.* TO '$USUARIO'@'%';
FLUSH PRIVILEGES;
SQL
unset PASSWORD

# --- Respaldo diario: mysqldump -> /var/backups/crediexpress (14 días) y copia a Cloud Storage ---
cat > /usr/local/bin/respaldo-bd.sh <<'EOF'
#!/bin/bash
# Respaldo diario de la base de Credi Express (lo instala deploy/vm/instalar-mysql.sh).
set -euo pipefail
MD=http://metadata.google.internal/computeMetadata/v1
meta() { curl -sf -H 'Metadata-Flavor: Google' "$MD/instance/attributes/$1"; }
BASE=$(meta base-datos)
BUCKET=$(meta bucket-respaldos)
DIR=/var/backups/crediexpress
ARCHIVO="$BASE-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
mkdir -p "$DIR"
mysqldump --single-transaction --routines --triggers --set-gtid-purged=OFF --databases "$BASE" |
  gzip > "$DIR/$ARCHIVO"
find "$DIR" -name '*.sql.gz' -mtime +14 -delete
# Sin IP externa: llega a Cloud Storage por Private Google Access.
TOKEN=$(curl -sf -H 'Metadata-Flavor: Google' "$MD/instance/service-accounts/default/token" |
  python3 -c 'import sys, json; print(json.load(sys.stdin)["access_token"])')
curl -sf -X POST --data-binary "@$DIR/$ARCHIVO" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/gzip' \
  "https://storage.googleapis.com/upload/storage/v1/b/$BUCKET/o?uploadType=media&name=$ARCHIVO" > /dev/null
echo "Respaldo $ARCHIVO subido a gs://$BUCKET"
EOF
chmod 750 /usr/local/bin/respaldo-bd.sh

# 09:00 UTC = 03:00 en Ciudad de México. La salida queda en syslog (journalctl -t respaldo-bd).
echo '0 9 * * * root /usr/local/bin/respaldo-bd.sh 2>&1 | logger -t respaldo-bd' > /etc/cron.d/respaldo-bd
chmod 644 /etc/cron.d/respaldo-bd

mkdir -p "$(dirname "$MARCA")"
date -u > "$MARCA"
estado listo

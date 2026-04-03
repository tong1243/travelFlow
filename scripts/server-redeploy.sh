#!/usr/bin/env bash
set -euo pipefail

ZIP_PATH="${1:-/opt/travelflow.zip}"
APP_DIR="${2:-/opt/travelflow}"
WEB_ROOT="${3:-/var/www/travelflow}"
SERVICE_NAME="${4:-travelflow}"

ENV_DIR="/etc/travelflow"
ENV_FILE="${ENV_DIR}/travelflow.env"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
NGINX_CONF="/etc/nginx/sites-available/travelflow-ip"
BACKUP_DIR="${APP_DIR}.prev"

log() {
  echo "[redeploy] $1"
}

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing command: $1"
    exit 1
  fi
}

if [[ "${EUID}" -ne 0 ]]; then
  echo "Please run as root."
  exit 1
fi

need_cmd unzip
need_cmd docker-compose
need_cmd mvn
need_cmd npm
need_cmd nginx
need_cmd systemctl
need_cmd java

if [[ ! -f "${ZIP_PATH}" ]]; then
  echo "Zip not found: ${ZIP_PATH}"
  exit 1
fi

WORK_DIR="$(mktemp -d /opt/travelflow_update_XXXXXX)"
trap 'rm -rf "${WORK_DIR}"' EXIT

log "Unpacking ${ZIP_PATH} ..."
unzip -oq "${ZIP_PATH}" -d "${WORK_DIR}"

if [[ ! -f "${WORK_DIR}/pom.xml" || ! -d "${WORK_DIR}/demo-app" || ! -d "${WORK_DIR}/frontend" ]]; then
  echo "Uploaded package is invalid. Expected pom.xml/demo-app/frontend at archive root."
  exit 1
fi

log "Switching code directory ..."
rm -rf "${BACKUP_DIR}"
if [[ -d "${APP_DIR}" ]]; then
  mv "${APP_DIR}" "${BACKUP_DIR}"
fi
mv "${WORK_DIR}" "${APP_DIR}"
mkdir -p "${APP_DIR}"

log "Starting dependencies (mysql/redis/qdrant) ..."
cd "${APP_DIR}"
docker-compose up -d mysql redis qdrant

log "Building frontend ..."
pushd frontend >/dev/null
npm ci
npm run build
popd >/dev/null

log "Publishing frontend to ${WEB_ROOT} ..."
mkdir -p "${WEB_ROOT}"
find "${WEB_ROOT}" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
cp -R "${APP_DIR}/frontend/dist/." "${WEB_ROOT}/"

log "Building backend ..."
mvn -pl demo-app -am -DskipTests package
JAR_PATH="$(ls -1 "${APP_DIR}/demo-app/target/"*.jar | grep -vE '(sources|javadoc|original)' | head -n 1 || true)"
if [[ -z "${JAR_PATH}" ]]; then
  echo "Could not find built jar in ${APP_DIR}/demo-app/target"
  exit 1
fi
cp -f "${JAR_PATH}" "${APP_DIR}/demo-app/target/app.jar"

if [[ ! -f "${ENV_FILE}" ]]; then
  log "Creating ${ENV_FILE} template ..."
  mkdir -p "${ENV_DIR}"
  JWT_SECRET="$(openssl rand -hex 32)"
  cat > "${ENV_FILE}" <<EOF
DASHSCOPE_API_KEY=
APP_JWT_SECRET=${JWT_SECRET}
APP_CORS_ALLOWED_ORIGINS=http://47.114.91.243
APP_JWT_EXPIRE_SECONDS=86400
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
QDRANT_URL=http://127.0.0.1:6333
EOF
fi

log "Writing systemd service ${SERVICE_NAME} ..."
cat > "${SERVICE_FILE}" <<EOF
[Unit]
Description=TravelFlow Spring Boot Service
After=network.target docker.service
Requires=docker.service

[Service]
Type=simple
WorkingDirectory=${APP_DIR}
EnvironmentFile=${ENV_FILE}
ExecStart=/usr/bin/java -jar ${APP_DIR}/demo-app/target/app.jar
Restart=always
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF

log "Writing nginx config ..."
cat > "${NGINX_CONF}" <<EOF
server {
    listen 80 default_server;
    server_name _;

    root ${WEB_ROOT};
    index index.html;

    location / {
        try_files \$uri \$uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 3600;
        proxy_send_timeout 3600;
        proxy_buffering off;
        gzip off;
    }
}
EOF

ln -sf "${NGINX_CONF}" /etc/nginx/sites-enabled/travelflow-ip
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx

log "Restarting service ${SERVICE_NAME} ..."
systemctl daemon-reload
systemctl enable "${SERVICE_NAME}"
systemctl restart "${SERVICE_NAME}"
sleep 3

log "Done. Quick checks:"
systemctl --no-pager --full status "${SERVICE_NAME}" | sed -n '1,30p'
echo "---"
curl -I -s http://127.0.0.1/ | head -n 1 || true
echo "---"
curl -i -s http://127.0.0.1/api/v1/health | sed -n '1,12p' || true


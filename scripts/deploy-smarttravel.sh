#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   sudo bash scripts/deploy-smarttravel.sh smarttravel.cn admin@smarttravel.cn
#
# Args:
#   $1 domain base (default: smarttravel.cn)
#   $2 letsencrypt email (optional)
#
# Required:
#   /etc/travelflow/travelflow.env must exist and contain:
#   - DASHSCOPE_API_KEY
#   - APP_JWT_SECRET

DOMAIN_BASE="${1:-smarttravel.cn}"
LE_EMAIL="${2:-}"

if [[ "${DOMAIN_BASE}" == www.* ]]; then
  DOMAIN_BASE="${DOMAIN_BASE#www.}"
fi

DOMAIN_WWW="www.${DOMAIN_BASE}"
REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WEB_ROOT="/var/www/travelflow"
ENV_DIR="/etc/travelflow"
ENV_FILE="${ENV_DIR}/travelflow.env"
NGINX_CONF="/etc/nginx/sites-available/smarttravel.cn"
SERVICE_FILE="/etc/systemd/system/travelflow.service"

log() {
  echo "[deploy] $1"
}

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing command: $1"
    exit 1
  fi
}

if [[ "${EUID}" -ne 0 ]]; then
  echo "Please run as root: sudo bash scripts/deploy-smarttravel.sh ${DOMAIN_BASE} ${LE_EMAIL}"
  exit 1
fi

need_cmd docker
need_cmd nginx
need_cmd mvn
need_cmd npm
need_cmd node
need_cmd java

if [[ ! -f "${ENV_FILE}" ]]; then
  mkdir -p "${ENV_DIR}"
  cat > "${ENV_FILE}" <<EOF
# Required
DASHSCOPE_API_KEY=
APP_JWT_SECRET=

# Optional (defaults are fine for this project)
APP_CORS_ALLOWED_ORIGINS=https://${DOMAIN_WWW},https://${DOMAIN_BASE}
APP_JWT_EXPIRE_SECONDS=86400
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
QDRANT_URL=http://127.0.0.1:6333
EOF
  echo "Created ${ENV_FILE}. Please fill DASHSCOPE_API_KEY and APP_JWT_SECRET, then rerun."
  exit 1
fi

if ! grep -Eq '^DASHSCOPE_API_KEY=.+$' "${ENV_FILE}"; then
  echo "DASHSCOPE_API_KEY is empty in ${ENV_FILE}"
  exit 1
fi
if ! grep -Eq '^APP_JWT_SECRET=.+$' "${ENV_FILE}"; then
  echo "APP_JWT_SECRET is empty in ${ENV_FILE}"
  exit 1
fi

log "Using repo: ${REPO_DIR}"
cd "${REPO_DIR}"

log "Starting dependencies via docker compose (mysql/redis/qdrant)..."
docker compose up -d mysql redis qdrant

log "Building frontend..."
pushd frontend >/dev/null
npm ci
npm run build
popd >/dev/null

log "Publishing frontend to ${WEB_ROOT} ..."
mkdir -p "${WEB_ROOT}"
find "${WEB_ROOT}" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
cp -R "${REPO_DIR}/frontend/dist/." "${WEB_ROOT}/"

log "Building backend..."
mvn -pl demo-app -am -DskipTests package

JAR_PATH="$(ls -1 "${REPO_DIR}/demo-app/target/"*.jar | grep -vE '(sources|javadoc|original)' | head -n 1 || true)"
if [[ -z "${JAR_PATH}" ]]; then
  echo "Could not find built jar in demo-app/target"
  exit 1
fi
cp -f "${JAR_PATH}" "${REPO_DIR}/demo-app/target/app.jar"

log "Writing systemd service..."
cat > "${SERVICE_FILE}" <<EOF
[Unit]
Description=TravelFlow Spring Boot Service
After=network.target docker.service
Requires=docker.service

[Service]
Type=simple
WorkingDirectory=${REPO_DIR}
EnvironmentFile=${ENV_FILE}
ExecStart=/usr/bin/java -jar ${REPO_DIR}/demo-app/target/app.jar
Restart=always
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF

log "Writing nginx site config..."
cat > "${NGINX_CONF}" <<EOF
server {
    listen 80;
    server_name ${DOMAIN_BASE} ${DOMAIN_WWW};

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

ln -sf "${NGINX_CONF}" /etc/nginx/sites-enabled/smarttravel.cn
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx

log "Enabling and restarting backend service..."
systemctl daemon-reload
systemctl enable travelflow
systemctl restart travelflow

if [[ -n "${LE_EMAIL}" ]]; then
  need_cmd certbot
  log "Requesting HTTPS certificate..."
  certbot --nginx -d "${DOMAIN_BASE}" -d "${DOMAIN_WWW}" \
    --agree-tos --non-interactive -m "${LE_EMAIL}" --redirect
else
  log "Skipping HTTPS certificate (no email provided)."
  log "Run manually when DNS is ready:"
  log "certbot --nginx -d ${DOMAIN_BASE} -d ${DOMAIN_WWW}"
fi

log "Done."
log "Backend status: systemctl status travelflow --no-pager"
log "Backend logs: journalctl -u travelflow -f"

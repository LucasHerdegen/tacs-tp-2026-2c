#!/usr/bin/env bash
set -euo pipefail

CONFIG_DIR="/root/.config/tacs"
mkdir -p "${CONFIG_DIR}"

write_bootstrap_token() {
  cat > "${CONFIG_DIR}/token.json" <<EOF
{
  "access_token": "ya29.bootstrap",
  "token_type": "Bearer",
  "refresh_token": "1//bootstrap-refresh",
  "expiry": "2020-01-01T00:00:00Z"
}
EOF
  chmod 600 "${CONFIG_DIR}/token.json"
}

if [ -n "${LEGACY_CLIMA_TOKEN_JSON:-}" ]; then
  echo "${LEGACY_CLIMA_TOKEN_JSON}" > "${CONFIG_DIR}/token.json"
  chmod 600 "${CONFIG_DIR}/token.json"
else
  echo "[clima-legacy] Iniciando bootstrap OAuth headless para ${LEGACY_CLIMA_ACCOUNT_EMAIL:-tintili@frba.utn.edu.ar}..."
  rm -f /tmp/oauth-bootstrap-cert.pem /tmp/oauth-bootstrap-key.pem
  python3 /app/bootstrap_oauth.py &
  while [ ! -f /tmp/oauth-bootstrap-cert.pem ]; do
    sleep 0.1
  done
  cat /tmp/oauth-bootstrap-cert.pem >> /etc/ssl/certs/ca-certificates.crt
  if ! grep -q "oauth2.googleapis.com" /etc/hosts; then
    echo "127.0.0.1 oauth2.googleapis.com www.googleapis.com" >> /etc/hosts
  fi
  write_bootstrap_token
fi

echo "[clima-legacy] Iniciando forwarder socat 0.0.0.0:9978 -> 127.0.0.1:9977..."
socat TCP-LISTEN:9978,fork,reuseaddr,bind=0.0.0.0 TCP:127.0.0.1:9977 &

echo "[clima-legacy] Iniciando supervisor de proceso /app/tacs-linux-amd64..."
while true; do
  if [ -z "${LEGACY_CLIMA_TOKEN_JSON:-}" ] && [ ! -f "${CONFIG_DIR}/token.json" ]; then
    write_bootstrap_token
  fi
  set +e
  /app/tacs-linux-amd64
  EXIT_CODE=$?
  set -e
  echo "[clima-legacy] El binario finalizo (exit code=${EXIT_CODE}). Reiniciando automaticamente en 1s..."
  sleep 1
done

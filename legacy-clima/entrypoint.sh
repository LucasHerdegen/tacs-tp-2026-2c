#!/usr/bin/env bash
set -euo pipefail

CONFIG_DIR="/root/.config/tacs"
TOKEN_FILE="${CONFIG_DIR}/token.json"
MOUNTED_TOKEN_FILE="/run/secrets/clima-legacy/token.json"
mkdir -p "${CONFIG_DIR}"

if [ -n "${LEGACY_CLIMA_TOKEN_JSON:-}" ]; then
  printf '%s\n' "${LEGACY_CLIMA_TOKEN_JSON}" > "${TOKEN_FILE}"
  chmod 600 "${TOKEN_FILE}"
  echo "[clima-legacy] Sesion cargada desde la variable de entorno LEGACY_CLIMA_TOKEN_JSON."
elif [ -f "${MOUNTED_TOKEN_FILE}" ] && [ -s "${MOUNTED_TOKEN_FILE}" ]; then
  cp "${MOUNTED_TOKEN_FILE}" "${TOKEN_FILE}"
  chmod 600 "${TOKEN_FILE}"
  echo "[clima-legacy] Sesion cargada desde el secreto montado (${MOUNTED_TOKEN_FILE})."
elif [ -f "${TOKEN_FILE}" ] && [ -s "${TOKEN_FILE}" ]; then
  echo "[clima-legacy] Sesion encontrada en ${TOKEN_FILE}."
else
  cat >&2 <<'EOF'
[clima-legacy] ERROR: No se encontro una sesion OAuth real (token.json) para el proveedor de clima legacy.

Para generar la sesion con tu cuenta institucional (@frba.utn.edu.ar):
  1. Ejecuta una sola vez el binario del proveedor legacy en tu maquina local:
       - Windows: .\bin\bin\tacs-windows-amd64.exe
       - Linux:   gzip -dc legacy-clima/tacs-linux-amd64.gz > /tmp/tacs-linux-amd64 && chmod +x /tmp/tacs-linux-amd64 && /tmp/tacs-linux-amd64
       - macOS:   ./bin/bin/tacs-darwin-arm64 (o tacs-darwin-amd64)
     y completa el inicio de sesion de Google con tu cuenta @frba.utn.edu.ar.
  2. Copia el API token impreso en consola a LEGACY_CLIMA_API_TOKEN en tu archivo .env.
  3. Inyecta el archivo token.json generado por el binario (sin versionarlo en Git):
       - Local (Docker Compose): copia token.json en legacy-clima/secrets/token.json
         (origen: %APPDATA%\tacs\token.json en Windows, ~/.config/tacs/token.json en Linux,
         ~/Library/Application Support/tacs/token.json en macOS), o configura LEGACY_CLIMA_TOKEN_DIR en .env.
       - Nube / sin volumenes: define la variable secreta LEGACY_CLIMA_TOKEN_JSON con el JSON completo.

Consulta README.md para el paso a paso detallado.
EOF
  exit 1
fi

LISTEN_PORT="${PORT:-9978}"
echo "[clima-legacy] Iniciando forwarder socat 0.0.0.0:${LISTEN_PORT} -> 127.0.0.1:9977..."
socat TCP-LISTEN:${LISTEN_PORT},fork,reuseaddr,bind=0.0.0.0 TCP:127.0.0.1:9977 &
SOCAT_PID=$!

cleanup() {
  kill "${APP_PID:-}" "${SOCAT_PID:-}" 2>/dev/null || true
  exit 0
}
trap cleanup TERM INT

echo "[clima-legacy] Iniciando supervisor de proceso /app/tacs-linux-amd64..."
while true; do
  set +e
  /app/tacs-linux-amd64 &
  APP_PID=$!
  wait "${APP_PID}"
  EXIT_CODE=$?
  set -e
  echo "[clima-legacy] El binario finalizo (exit code=${EXIT_CODE}). Reiniciando automaticamente en 1s..."
  sleep 1
done

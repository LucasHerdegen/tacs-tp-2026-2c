---
name: docker-verify
description: >-
  Validates docker-compose.yaml configuration, builds and launches the full 8-service
  stack (MongoDB, clima-legacy, backend, frontend, Jaeger, OTel Collector, Prometheus,
  Grafana), and verifies container health and logs. Use when modifying Dockerfiles,
  docker-compose.yaml, environment variables, or service integration.
---

# Skill: Validación de Contenedores e Infraestructura (`docker-compose.yaml`)

## Propósito
Garantizar que los **8 servicios** definidos en `docker-compose.yaml` (`mongodb`, `clima-legacy`, `jaeger`, `otel-collector`, `prometheus`, `grafana`, `backend`, `frontend`) se validen, construyan, comuniquen en la red interna y permanezcan estables.

## Cuándo activar esta Skill
- Al modificar cualquier `Dockerfile` (`backend/`, `frontend/`, `legacy-clima/`), `docker-compose.yaml`, `observability/` o variables de entorno en `application.properties` / `.env.example`.
- Cuando un servicio reporte fallos de arranque, conexión a MongoDB, `clima-legacy` o exportación OTLP.

## Instrucciones paso a paso (desde la raíz del repositorio)

1. **Verificar prerequisitos `.env` y sesión real de `clima-legacy` (sin exponer secretos):**
   Confirmá que exista `.env` en la raíz (sin imprimir su contenido en consola ni logs) y que exista una sesión real para `clima-legacy` (`legacy-clima/secrets/token.json`, `LEGACY_CLIMA_TOKEN_DIR` o `LEGACY_CLIMA_TOKEN_JSON`). Si falta alguno, advertí al usuario siguiendo el paso a paso del `README.md`.

2. **Validación de sintaxis Compose:**
   ```powershell
   docker compose config -q
   ```
   *(Corregí cualquier error de sintaxis o variable faltante antes de levantar).*

3. **Construcción y despliegue:**
   ```powershell
   docker compose up -d --build
   ```

4. **Espera y verificación de estado:**
   Aguardá ~15–20 segundos a que inicien `mongodb` (`service_healthy`), `clima-legacy` y Spring Boot (`backend`), luego verificá:
   ```powershell
   docker compose ps
   ```
   Todos los servicios deben figurar `Up` / `running` (y `healthy` en `mongodb` y `clima-legacy`).

5. **Comprobación funcional básica por servicio:**
   - **Frontend:** `http://localhost:80` responde HTTP `200`.
   - **Backend:** `http://localhost:${BACKEND_PORT:-8080}/v3/api-docs` o `/actuator/health` responde correctamente.
   - **Clima Legacy (`clima-legacy`):** Debe permanecer **sin puertos publicados al host**; verificá vía logs (`docker compose logs --tail=30 clima-legacy`) que restauró la sesión real (`session restored`), levantó `socat` en `:9978` y el supervisor de `/app/tacs-linux-amd64`.
   - **Observabilidad:** `jaeger` (`:16686`), `prometheus` (`:9090`), `grafana` (`:3000`).

6. **Diagnóstico de fallos:**
   Si algún contenedor está en `Restarting` o `Exited`, inspeccioná sus logs:
   ```powershell
   docker compose logs --tail=100 <nombre_del_servicio>
   ```
   Corregí la causa y repetí desde el paso 3.

7. **Limpieza (Opcional):**
   - Para detener contenedores manteniendo volúmenes/datos locales: `docker compose down`
   - **No ejecutes `docker compose down -v` automáticamente** salvo que el usuario pida explícitamente una limpieza total de volúmenes efímeros.

## Formato de Reporte
- **Estado de Infraestructura:** [Estable / Inestable]
- **Servicios Activos:** [X de 8] servicios en estado Up/Healthy.
- **Observaciones:** (Logs relevantes, puertos/endpoints verificados o fallos corregidos).

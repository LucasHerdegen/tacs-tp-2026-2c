---
name: observability-verify
description: >-
  Verifies end-to-end OpenTelemetry traces and metrics across tacs-frontend, tacs-backend,
  OTel Collector, Jaeger, Prometheus, and Grafana, including load tests. Use when modifying
  ObservabilidadBusquedas, ObservabilidadConfig, frontend telemetry.ts, observability/
  configs, or activity search endpoints.
---

# Skill: Verificación End-to-End de Observabilidad (TP5)

## Propósito
Asegurar que la instrumentación OpenTelemetry del flujo crítico **Búsqueda de actividades disponibles** (`tacs-frontend` -> `GET /api/actividades` en `tacs-backend` -> MongoDB) genere trazas correlacionadas en Jaeger, métricas acotadas en Prometheus/Grafana y no exponga PII ni ruido fuera de alcance.

## Cuándo activar esta Skill
- Al modificar `ObservabilidadBusquedas.java`, `ObservabilidadConfig.java`, `ActividadesServiceImplem.java`, `ActividadesRepositoryImpl.java`, `frontend/src/telemetry.ts`, `ActivitySearch.tsx`, `observability/` o `scripts/load-test-observabilidad.ps1`.

## Instrucciones paso a paso

1. **Tests unitarios de telemetría (sin levantar stack):**
   Desde `backend/`:
   ```powershell
   .\mvnw.cmd test -Dtest=ObservabilidadBusquedasTest
   ```
   Validá creación de `actividad.buscar` y `actividad.buscar.db`, registro de `actividad.busquedas.completadas`, `actividad.busqueda.db.duration`, `StatusCode.ERROR` en fallos y ausencia de texto libre o IDs sensibles.

2. **Verificar compilación de Frontend y propagación acotada:**
   Confirmá en `frontend/src/telemetry.ts` que `FetchInstrumentation` conserve `ignoreUrls: [/^(?!.*\/api\/actividades\?).*$/]` y ejecutá `npm run lint && npm run build` desde `frontend/`.

3. **Levantamiento del stack y prueba de carga reproducible (desde raíz):**
   Con el stack arriba (`docker compose up -d --build`), ejecutá:
   ```powershell
   pwsh -File ./scripts/load-test-observabilidad.ps1
   ```
   *(Si `BACKEND_PORT` es distinto de `8080`, pasar `-BaseUrl "http://localhost:<puerto>"`).*

4. **Verificación de señales en Jaeger, Prometheus y Grafana:**
   - **Jaeger (`http://localhost:16686`):** Confirmar trazas con servicio `tacs-backend` (y `tacs-frontend` al probar desde la web) conteniendo `GET /api/actividades` -> `actividad.buscar` -> `actividad.buscar.db`, además del escenario `400 ProblemDetail` (`fechaInicio > fechaFin`) marcado en error sin crear `actividad.buscar.db`.
   - **Prometheus (`http://localhost:9090`):** Verificar las series `actividad_busquedas_completadas_total`, `actividad_busqueda_db_duration_seconds_bucket` y `http_server_request_duration_seconds_bucket`.
   - **Grafana (`http://localhost:3000/d/tp5-busqueda-actividades`):** Confirmar que el dashboard aprovisionado carga sus 5 paneles sin errores de query.

## Formato de Reporte
- **Unit tests OTel (`ObservabilidadBusquedasTest`):** [OK / Fallido]
- **Trazas en Jaeger:** [Spans verificados + traza de error 400]
- **Métricas en Prometheus / Grafana:** [Series activas y paneles]
- **Privacidad y Cardinalidad:** [Confirmar ausencia de PII/texto libre en atributos]

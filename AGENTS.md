# Reglas del Proyecto TACS — "404 Sol not found"

Este repositorio implementa una plataforma para organizar actividades grupales, monitorear pronósticos climáticos (WeatherAPI y proveedor Legacy) y gestionar votaciones de reprogramación, con interfaz Web, API REST, Bot de Telegram y stack completo de observabilidad OpenTelemetry.

## Reglas Generales

- **Herramientas nativas de edición:** NO utilices scripts ad-hoc de Python o Bash para leer, escribir o modificar código fuente; utilizá exclusivamente las herramientas nativas de lectura y edición del agente.
- **Idioma y commits:** La comunicación, documentación, mensajes de commit y el código nuevo del backend deben escribirse en español.
- **Seguridad y secretos:** Jamás hardcodees credenciales, tokens ni API keys en el código o documentación. Leé siempre estos valores desde variables de entorno (ver `.env.example`) y nunca commitees `.env`, `**/token.json` (`legacy-clima`) ni carpetas locales ignoradas.
- **Reglas por subproyecto:** Al trabajar dentro de `backend/` o `frontend/`, aplican automáticamente [backend/AGENTS.md](backend/AGENTS.md) y [frontend/AGENTS.md](frontend/AGENTS.md). Si tu harness no carga `AGENTS.md` anidados, leelos explícitamente antes de editar código en esas carpetas.

## Estructura del Repositorio

- `backend/` — Monolito modular **Java 21 + Spring Boot 4.1.1** (`com.tacs.backend`), Maven Wrapper (`./mvnw` / `.\mvnw.cmd`), MongoDB, ShedLock, Resilience4j, Bot de Telegram (`java-telegram-bot-api`) y OpenTelemetry API (`ObservabilidadBusquedas`).
- `frontend/` — SPA **React 19 + TypeScript + Vite** con Tailwind CSS v4, Leaflet y `@opentelemetry/sdk-trace-web` (`src/telemetry.ts`).
- `legacy-clima/` — Contenedor Docker para el binario `tacs-linux-amd64`: puente TCP `socat` (`0.0.0.0:9978` -> `127.0.0.1:9977`), supervisor ante crashes (`os.Exit(1)`) e inyección de sesión real `token.json` (volumen `:ro` desde `legacy-clima/secrets/` o variable `LEGACY_CLIMA_TOKEN_JSON`).
- `observability/` — Configuración de `otel-collector`, `prometheus` y aprovisionamiento de datasources/dashboards de `grafana` (`tp5-busqueda-actividades`).
- `scripts/` — Scripts PowerShell reproducibles de pruebas de carga (`load-test-legacy.ps1`, `load-test-observabilidad.ps1`).
- `docs/` — Registro de decisiones de arquitectura ([docs/ADRs.md](docs/ADRs.md)), ingeniería inversa y resiliencia del servicio legacy ([docs/proveedor-clima-legacy.md](docs/proveedor-clima-legacy.md)) e histórico de prompts ([docs/prompts.md](docs/prompts.md)).
- `prompts/` — Archivo histórico de prompts utilizados durante el TP (conservar sin cambios salvo pedido expreso).
- `diagramas/` — Diagramas de clases e integración (`class-diagram.puml`, `notification-integration.md`).
- `correcciones/` *(local, ignorado en Git)* — Devoluciones de entregas (`correcciones-entrega-*.txt`) y subcarpeta `correcciones/contexto/` con los **enunciados oficiales de los Trabajos Prácticos** (`TP TACS 2026C2.md`, Entrega 4, TP5 Observability). Úsala como referencia de requisitos cuando esté disponible localmente; nunca intentes versionarla.
- `bin/` *(local, ignorado en Git)* — Binarios originales del proveedor de clima legacy provistos por la cátedra.
- `docker-compose.yaml` y `.env.example` — Definición del stack completo de 8 servicios (`mongodb`, `clima-legacy`, `jaeger`, `otel-collector`, `prometheus`, `grafana`, `backend`, `frontend`).
- `.github/workflows/ci.yml` — Pipeline de CI para compilar/testear backend (con `mongo:6`) y lint/build de frontend.
- `.agents/` — Configuración del harness: skills en `.agents/skills/` y hooks de ciclo de vida en `.agents/hooks.json`.

## Comandos de Verificación Rápida

- **Backend** (ejecutar desde `backend/`, usando `.\mvnw.cmd` en Windows PowerShell o `./mvnw` en Linux/macOS/Bash):
  - Compilar: `.\mvnw.cmd clean compile`
  - Tests: `.\mvnw.cmd test` (o `.\mvnw.cmd test -Dtest=NombreClaseTest`)
  - Formato (Spotless): `.\mvnw.cmd spotless:check` (aplicar solo sobre archivos tocados o cuando corresponda)
- **Frontend** (ejecutar desde `frontend/`):
  - Lint: `npm run lint`
  - Type-check y build: `npm run build`
- **Infraestructura Docker** (desde la raíz):
  - Validar Compose: `docker compose config`
  - Levantar stack: `docker compose up -d --build`
  - Estado y logs: `docker compose ps` / `docker compose logs <servicio>`

## Skills Disponibles (`.agents/skills/`)

- `verify-build` — Verificación integral de Backend (compilación, Spotless, tests) y Frontend (ESLint, TypeScript, Vite build).
- `feature-testing` — Diseño y ejecución de pruebas por comportamiento en backend y frontend.
- `implementation-plan` — Confección de plan técnico previo para features grandes, integraciones complejas o cambios arquitectónicos.
- `docker-verify` — Validación del stack completo definido en `docker-compose.yaml`.
- `observability-verify` — Verificación end-to-end de trazas, métricas y pruebas de carga OpenTelemetry (Jaeger, Prometheus, Grafana).

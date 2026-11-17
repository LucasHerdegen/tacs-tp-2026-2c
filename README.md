# TACS - Trabajo Práctico

Este repositorio contiene el backend y el frontend del trabajo práctico para la materia **TACS** (Tecnologías Avanzadas en la Construcción de Software), desarrollado en **Java 21** con **Spring Boot** y **React + TypeScript**.

El sistema permite organizar actividades (tanto en espacios cerrados como al aire libre), gestionar participantes, elegir el proveedor de pronóstico climático (**WeatherAPI** o **Proveedor Legacy**), chequear las condiciones climáticas de forma periódica e iniciar votaciones de reprogramación (con quórum) en caso de que el clima sea desfavorable o el organizador deba cancelar el evento.

## Requisitos Previos

- **Docker** y **Docker Compose**
- **Java 21** y **Maven** (sólo para desarrollo local fuera de contenedor)

## Configuración y Ejecución

El proyecto está dockerizado para cumplir con los requisitos de portabilidad.

### 1. Variables de Entorno Requeridas

Antes de levantar el entorno, podés configurar el archivo `.env` en la raíz del proyecto (basado en `.env.example`) o tener las variables exportadas:

- `JWT_SECRET`: (Requerida) Clave secreta para la validación de tokens JWT.
- `ADMIN_USERNAME`: (Opcional) Usuario para la cuenta de administrador.
- `ADMIN_PASSWORD`: (Opcional) Contraseña para la cuenta de administrador.
- `MONGODB_URI`: (Opcional) URI de conexión a MongoDB. Por defecto usa la generada por Docker Compose.
- `WEATHERAPI_API_KEY`: (Requerida) API key de [WeatherAPI.com](https://www.weatherapi.com/).
- `TELEGRAM_BOT_TOKEN`: (Requerida) Token del bot, obtenido de `@BotFather`. Ver sección [Bot de Telegram](#bot-de-telegram).
- `TELEGRAM_BOT_USERNAME`: (Requerida) Username del bot (sin la `@`), también entregado por `@BotFather`.
- `LEGACY_CLIMA_ACCOUNT_EMAIL`: (Opcional) Cuenta institucional `@frba.utn.edu.ar` utilizada para la sesión del servicio de clima legacy.
- `LEGACY_CLIMA_API_TOKEN`: (Requerida para usar el proveedor Legacy) Token Bearer emitido por el binario de *Humedad, Datos y Pronósticos S.A.* al autenticar la cuenta institucional.
- `LEGACY_CLIMA_BASE_URL`: (Opcional) URL interna del servicio legacy (por defecto `http://clima-legacy:9978` en Docker Compose).
- `BACKEND_PORT`: (Opcional) Puerto del host mapeado al backend (por defecto `8080`; útil si `8080` ya está ocupado localmente, por ejemplo `BACKEND_PORT=8081`).
- `OBSERVABILIDAD_BUSQUEDA_DEMORA_MS`: (Opcional) Demora controlada en milisegundos sobre el acceso a MongoDB en búsqueda de actividades (por defecto `0`, deshabilitada).
- `GF_SECURITY_ADMIN_USER` / `GF_SECURITY_ADMIN_PASSWORD`: (Opcional) Credenciales de Grafana (por defecto `admin` / `admin`, además de acceso anónimo con rol `Viewer`).

### 2. Ejecutar la Aplicación (Docker)

Para iniciar todos los servicios junto con su red interna aislada, la base de datos y el stack completo de observabilidad:

```bash
docker compose up --build -d
```
*(Usamos `--build` para asegurarnos de que las imágenes se recompilen con los últimos cambios en el código, y `-d` para que corran en segundo plano).*

Esto levantará:
- El **Frontend** (React + Vite, servido con Nginx) en el puerto `80`: **http://localhost** (también proxya `/api/` al backend y `/otel/` al Collector OTLP).
- El **Backend** (Spring Boot con `opentelemetry-javaagent.jar` v`2.33.1`) en el puerto `${BACKEND_PORT:-8080}` (por defecto `8080`).
- Una instancia de **MongoDB** en el puerto `27017`.
- El servicio **Clima Legacy** (`clima-legacy`) accesible únicamente dentro de la red interna de Docker en `http://clima-legacy:9978`, con supervisión y reinicio automático ante fallos.
- **OpenTelemetry Collector** (`otel/opentelemetry-collector-contrib`) en puertos `4317` (OTLP gRPC), `4318` (OTLP HTTP) y `8889` (endpoint `/metrics` para Prometheus).
- **Jaeger** (`jaegertracing/all-in-one`) en **http://localhost:16686** para visualizar trazas distribuidas (`MEMORY_MAX_TRACES=1500`).
- **Prometheus** (`prom/prometheus`) en **http://localhost:9090** con scrapeo cada `5s` sobre `otel-collector:8889`.
- **Grafana** (`grafana/grafana`) en **http://localhost:3000** con datasource y dashboard aprovisionados automáticamente (`TP5 Observabilidad - Búsqueda de Actividades`, `uid: tp5-busqueda-actividades`).

> **Documentación de Entrega 4 (Proveedor de Clima Legacy):** Todos los detalles de arquitectura, modos de falla descubiertos mediante ingeniería inversa del binario, decisiones de resiliencia (ADRs), diagrama de despliegue y resultados de las pruebas de carga se encuentran documentados en [`docs/proveedor-clima-legacy.md`](docs/proveedor-clima-legacy.md).

> **Decisiones de Arquitectura (ADRs):** El registro completo de decisiones (incluida la **ADR 13** de Observabilidad) se encuentra en [`docs/ADRs.md`](docs/ADRs.md).

### 3. Documentación de la API (OpenAPI / Swagger)

Una vez que el contenedor esté corriendo, podés acceder a la interfaz gráfica de Swagger en:
**http://localhost:8080/swagger-ui.html** o a la especificación en JSON en **http://localhost:8080/v3/api-docs** (ajustando el puerto si definiste `BACKEND_PORT`).

### 4. Ejecutar los Tests Locales

Para correr la suite completa (que incluye pruebas unitarias con JUnit/Mockito, `@WebMvcTest`, pruebas OpenTelemetry en `ObservabilidadBusquedasTest` y tests de integración/concurrencia `@DataMongoTest` contra MongoDB real):

```bash
docker compose up -d mongodb
cd backend
./mvnw test
```

## Bot de Telegram

Además del frontend web y la API REST, el sistema expone una interfaz completa por Telegram: crear actividades, buscarlas, sumarse/bajarse, votar reprogramaciones y consultar el estado propio, todo con comandos y botones.

### 1. Crear el bot con @BotFather

1. Abrí una conversación con [`@BotFather`](https://t.me/BotFather) en Telegram.
2. Mandale `/newbot` y seguí las instrucciones (nombre visible y username, que debe terminar en `bot`).
3. `@BotFather` te va a devolver un **token** (formato `123456:ABC-DEF...`) — es el valor de `TELEGRAM_BOT_TOKEN`.
4. El **username** que elegiste (sin la `@`) es el valor de `TELEGRAM_BOT_USERNAME`.
5. Cargá ambos valores en tu `.env` y levantá el backend (`docker compose up --build -d`, o `./mvnw spring-boot:run` en local).

### 2. Probar el alta nativa (cuenta creada desde cero por Telegram)

Buscá tu bot por su username en Telegram y mandale `/start` sin ningún parámetro. Si el `chat_id` no está vinculado a ninguna cuenta todavía, el bot crea un `Usuario` nuevo en el momento (sin contraseña, identificado por ese `chat_id`) y queda listo para usar el resto de los comandos.

### 3. Probar la vinculación de una cuenta ya existente

1. Autenticate (login) y llamá a `POST /api/usuarios/me/telegram/vinculacion` con tu JWT. Devuelve un token de un solo uso y un `deepLink` con la forma `https://t.me/<bot>?start=<token>` (expira a los pocos minutos).
2. Abrí ese link (o mandale `/start <token>` directamente al bot). El chat queda vinculado a esa cuenta existente.

### 4. Comandos disponibles

Una vez identificado (por alta nativa o vinculación), mandale `/ayuda` al bot para ver el listado actualizado de comandos (`/crear`, `/buscar`, `/misactividades`, `/misvotaciones`, `/clima`, etc.).

## Observabilidad (TP5)

### Flujo Crítico Instrumentado

Se instrumentó de punta a punta el flujo **Búsqueda de actividades disponibles** (`tacs-frontend` -> `tacs-backend` -> MongoDB):

1. **Frontend (`tacs-frontend`, `frontend/src/telemetry.ts` y `ActivitySearch.tsx`)**:
   - Envuelve cada búsqueda en el span manual raíz **`actividad.buscar.frontend`** (`SpanKind.CLIENT`) con atributos no sensibles de filtros y paginación.
   - `FetchInstrumentation` propaga los encabezados W3C `traceparent` / `tracestate` hacia el backend (habilitados en CORS por `SecurityConfig.java`), **estrictamente acotada** a `/api/actividades?...` mediante `ignoreUrls: [/^(?!.*\/api\/actividades\?).*$/]`.
   - Exporta trazas vía OTLP HTTP (`/otel/v1/traces`, proxeado por Nginx/Vite hacia `otel-collector:4318`) sin ensuciar otras pantallas.
   - Si el usuario ingresa `fechaInicio > fechaFin`, la solicitud igualmente viaja al backend para que `GET /api/actividades` devuelva `400 ProblemDetail`, registrando el fallo controlado end-to-end (frontend + backend en rojo en Jaeger) sin incrementar el contador de búsquedas exitosas.

2. **Backend (`tacs-backend`, `ObservabilidadBusquedas.java` y `ObservabilidadConfig.java`)**:
   - **Auto-instrumentación** con `opentelemetry-javaagent.jar` (`v2.33.1`, exportando OTLP gRPC cada `5000 ms` y `OTEL_SEMCONV_STABILITY_OPT_IN=database`): captura el span servidor `GET /api/actividades`, `UsuarioMongoRepository.findById` (resolución de usuario autenticado), comandos MongoDB (`aggregate actividades`, `find actividades`, `find usuarios`) y las métricas estándar HTTP/DB.
   - **Instrumentación manual** usando exclusivamente `io.opentelemetry:opentelemetry-api` (`GlobalOpenTelemetry.get()`, desacoplado de vendor/SDK):
     - Span **`actividad.buscar`** (`SpanKind.INTERNAL`): envuelve `ActividadesServiceImplem#buscarActividades` con filtros booleanos/enums (cero PII, cero texto libre buscado, cero IDs), paginación y `actividad.resultados.*` al finalizar, o `recordException` + `StatusCode.ERROR` + `error.type` ante fallo.
     - Span hijo **`actividad.buscar.db`** (`SpanKind.INTERNAL`): envuelve `ActividadesRepositoryImpl#buscarActividades` (consulta `count`, `find` y mapeo `@DBRef` de documentos).

> **Alcance respecto a proveedores de clima:** La búsqueda de actividades disponibles consulta únicamente MongoDB (`actividades` y referencias a `usuarios`); **no invoca** a WeatherAPI ni al servicio `clima-legacy` (el pronóstico se consulta en creación/reprogramación/chequeos periódicos/detalle climático).

### Roles en la Arquitectura OpenTelemetry

| Componente | Tecnología / Archivo | Rol en el sistema |
| :--- | :--- | :--- |
| **OpenTelemetry API** | `opentelemetry-api` (Java) / `@opentelemetry/api` (TS) | Define las abstracciones neutrales (`Tracer`, `Span`, `Meter`, `LongCounter`, `DoubleHistogram`) usadas por el código de negocio (`ObservabilidadBusquedas`, `telemetry.ts`) sin acoplarse a un backend concreto. En tests unitarios se inyecta `OpenTelemetry.noop()` o `OpenTelemetrySdk`. |
| **OpenTelemetry Agent / SDK** | `opentelemetry-javaagent.jar v2.33.1` (`backend/Dockerfile`) / `@opentelemetry/sdk-trace-web` + `ZoneContextManager` | Provee la implementación en tiempo de ejecución: auto-instrumenta Spring Web MVC, driver MongoDB y `fetch` acotado, recolecta e exporta telemetría OTLP hacia el Collector (`4317` gRPC / `4318` HTTP). |
| **OpenTelemetry Collector** | `otel/opentelemetry-collector-contrib` (`observability/otel-collector/otel-collector-config.yaml`) | Punto central que desacopla la app de las herramientas de almacenamiento: recibe OTLP, aplica `memory_limiter` y `batch`, convierte atributos de recurso (`resource_to_telemetry_conversion`) y enruta trazas a `jaeger:4317` y métricas a Prometheus en `0.0.0.0:8889`. |
| **Jaeger** | `jaegertracing/all-in-one` (`http://localhost:16686`) | Almacena y grafica trazas distribuidas correlacionadas (`tacs-frontend` + `tacs-backend`), con `MEMORY_MAX_TRACES=1500` para evitar agotamiento de RAM bajo ráfagas de carga. |
| **Prometheus + Grafana** | `prom/prometheus` (`:9090`) + `grafana/grafana` (`:3000`) | Prometheus scrapea `otel-collector:8889` cada `5s`. Grafana aprovisiona el datasource (`prometheus`) y el dashboard **`TP5 Observabilidad - Búsqueda de Actividades`** (`uid: tp5-busqueda-actividades`) con 5 paneles. |

### Métricas Registradas y Paneles de Grafana

| Métrica OpenTelemetry (Serie Prometheus) | Tipo / Unidad | Cuándo se registra | Atributos acotados relevantes |
| :--- | :--- | :--- | :--- |
| `actividad.busquedas.completadas` (`actividad_busquedas_completadas_total`) | `LongCounter` monotónico (`1`) — **Manual** | Al finalizar `actividad.buscar` **sin excepciones** (excluye `400` y fallos) | `actividad.tipo` (`TODOS`, `AIRE_LIBRE`, `TECHADA`, `MIXTA`), `actividad.estado`, `actividad.con_filtro_texto`, `actividad.con_filtro_fecha`, `actividad.con_cupo_disponible`, `service_name` |
| `actividad.busqueda.db.duration` (`actividad_busqueda_db_duration_seconds_*`) | `DoubleHistogram` (`s`, buckets `0.001` a `10.0`) — **Manual** | Bloque `finally` de `actividad.buscar.db` (`count`, `find` y mapeo) | `db.system=mongodb`, `db.collection=actividades`, `operacion=buscar_actividades`, `resultado=exito\|error`, `service_name` |
| `http.server.request.duration` (`http_server_request_duration_seconds_*`) | Histograma (`s`) — **Auto (Java Agent)** | Cada request HTTP atendida por Spring MVC | `service_name=tacs-backend`, `http_route=/api/actividades`, `http_request_method=GET`, `http_response_status_code` |

Paneles aprovisionados en **Grafana (`http://localhost:3000/d/tp5-busqueda-actividades`)**:
1. **Requests por segundo (`GET /api/actividades`)**: total, exitosos (`2xx`) y fallidos (`!~2..`).
2. **Porcentaje de requests fallidos (`GET /api/actividades`)**: tasa porcentual con guarda contra división por cero (`clamp_min`).
3. **Tiempo de respuesta p95 del flujo (HTTP)**: percentiles `p95` y `p50` de `GET /api/actividades`.
4. **Búsquedas completadas exitosamente por segundo**: tasa total y apertura por `actividad_tipo`.
5. **Duración p95 de accesos a MongoDB (búsqueda de actividades)**: percentiles `p95` y `p50` de `actividad_busqueda_db_duration_seconds_bucket`.

### Cómo Probar y Reproducir la Prueba de Carga

1. **Verificación interactiva desde la Web + Jaeger + Grafana**:
   - Ingresá a **http://localhost**, registrate o iniciá sesión e ir a búsqueda de actividades.
   - Ejecutá una búsqueda válida (p. ej. filtro `AIRE_LIBRE`) y luego una búsqueda inválida (`fechaInicio > fechaFin`, p. ej. `2026-12-31` a `2026-01-01`), que mostrará el error en UI devolviendo HTTP `400`.
   - En **Jaeger** (`http://localhost:16686`), seleccioná `tacs-frontend` o `tacs-backend` para inspeccionar la traza conectada (`actividad.buscar.frontend` -> `HTTP GET` -> `GET /api/actividades` -> `actividad.buscar` -> `actividad.buscar.db`), o filtrá spans en error para ver el fallo controlado sin `actividad.buscar.db`.
   - En **Grafana** (`http://localhost:3000`, `admin`/`admin`), abrí el dashboard **TP5 Observabilidad - Búsqueda de Actividades**.

2. **Demora controlada opcional vs saturación real por concurrencia**:
   - `ActividadesRepositoryImpl#medirConsultaDb` soporta una demora artificial opcional mediante **`OBSERVABILIDAD_BUSQUEDA_DEMORA_MS`** (`observabilidad.busqueda.demora-simulada-ms`, por defecto `0`), anotando `actividad.demora_simulada_ms` en el span activo cuando `> 0`.
   - **Demora simulada (`OBSERVABILIDAD_BUSQUEDA_DEMORA_MS > 0`)**: sirve para reproducir de forma determinista un servicio/DB lento en entorno local sin alta concurrencia (por ejemplo, seteando `OBSERVABILIDAD_BUSQUEDA_DEMORA_MS=250` en `.env` o Compose).
   - **Saturación real por concurrencia (`OBSERVABILIDAD_BUSQUEDA_DEMORA_MS=0`)**: las pruebas comparativas documentadas abajo se ejecutaron **sin demora simulada (`0 ms`)**, evidenciando contención genuina al escalar clientes concurrentes sobre recursos acotados.

3. **Ejecución del script reproducible de carga**:
   - El entorno restringe explícitamente los recursos por contenedor en `docker-compose.yaml`: `backend` (`1.5` CPUs, `1024m` RAM), `mongodb` (`1.0` CPU, `512m` RAM), `otel-collector` (`0.5` CPUs, `512m`), `jaeger` (`0.5` CPUs, `1024m`), `prometheus` (`0.5` CPUs, `512m`), `grafana` (`0.5` CPUs, `512m`), `frontend` (`0.5` CPUs, `256m`), `clima-legacy` (`0.5` CPUs, `256m`).
   - Ejecutar desde la raíz (si `BACKEND_PORT=8081`, pasar `-BaseUrl "http://localhost:8081"`):

```powershell
pwsh -File ./scripts/load-test-observabilidad.ps1
```

El script garantiza al menos `300` actividades en MongoDB (`SeedCount=300`), dispara un fallo controlado HTTP `400`, corre **Escenario 1** (`ConcurrenciaBaja=2`, `25s`), espera `15s`, corre **Escenario 2** (`ConcurrenciaAlta=24`, `25s`) y consulta Prometheus y Jaeger.

### Resultados Observados (2026-11-13, `300` actividades, `OBSERVABILIDAD_BUSQUEDA_DEMORA_MS=0`)

| Métrica | Escenario 1: Línea Base (`2` hilos, `25.02s`) | Escenario 2: Alta Concurrencia (`24` hilos, `25.2s`) | Variación / Interpretación |
| :--- | :--- | :--- | :--- |
| Requests totales / Error HTTP | `3019` req / `0` errores (`0%`) | `2643` req / `0` errores (`0%`) | Sin errores 5xx/timeouts; el sistema encola bajo carga |
| Throughput cliente / PromQL `req/s` | `120.64 req/s` / `121.91 req/s` | `104.87 req/s` / `106.52 req/s` | **No escala** (`-12.6%`): techo cercano a `105–122 req/s` |
| Búsquedas completadas/s (PromQL) | `121.91 /s` | `106.52 /s` | Coincide con `2xx` HTTP |
| Latencia cliente `p50` / `p95` / `max` | `14.45 ms` / `23.18 ms` / `90.16 ms` | `206.25 ms` / `303.88 ms` / `500.78 ms` | `p95` cliente crece **~13.1x** |
| Latencia `p95` HTTP (Prometheus) | `24.77 ms` | `460.32 ms` | Crece **~18.6x** |
| Latencia `p95` DB (`actividad.busqueda.db.duration`) | `24.7 ms` | `442.62 ms` | Crece **~17.9x**; explica el **96.2%** del `p95` HTTP |

**Trazas reales inspeccionadas en Jaeger**:
- **Frontend -> Backend exitosa (reciente)**: `traceID=56665532f68ccbb3fcdc01d1142ca4cb` (`9` spans) y página completa `traceID=5d3e3e6db8c555db15a6cf3b6bf287d8` (`49` spans incluyendo `@DBRef` de 20 ítems).
- **Fallo controlado HTTP 400**: desde UI `traceID=947b299ece493c20c3f2d01f8bac41b5` (`6` spans en `tacs-frontend` + `tacs-backend`, `actividad.buscar` marcado `ERROR` sin crear `actividad.buscar.db`) y desde carga `traceID=6bbe5a621dbe71bfcd143663a0e9aa87`.
- **Escenario 1 (`2` hilos)**: mediana `traceID=c74d0150ced9df74d7b86bd06ae32de7` (`47` spans: `HTTP=12.43 ms`, `JWT findById=0.39 ms`, `actividad.buscar=11.29 ms`, `actividad.buscar.db=11.27 ms` con `aggregate=0.39 ms`, `find=0.21 ms`, `find usuarios x41`); lenta `traceID=3d74e43af3dcf67cd8f4b312ad6e82c7` (`HTTP=15.93 ms`, `DB=14.6 ms`).
- **Escenario 2 (`24` hilos)**: mediana `traceID=11658bb83b54164689613a919895824c` (`47` spans: `HTTP=191.12 ms`, `JWT findById=0.3 ms`, `actividad.buscar=189.96 ms`, `actividad.buscar.db=189.93 ms` con `aggregate=0.57 ms`, `find=0.39 ms`, `find usuarios x41`); lenta `traceID=4efe9faf7a2b4dc224fbf6730e9a8e85` (`HTTP=207.51 ms`, `DB=205.76 ms` con `aggregate=87.07 ms`, `find=0.45 ms`, `find usuarios x41`).

### Análisis del Cuello de Botella (Preguntas TP5)

1. **¿Qué muestra la instrumentación bajo carga?**
   Al pasar de `2` a `24` hilos concurrentes (x12), el sistema alcanza saturación cerca de `106.5 req/s` (`0%` de errores): el throughput cae levemente (`121.91` -> `106.52 req/s`), mientras el `p95` HTTP se multiplica ~18.6x (`24.77 ms` -> `460.32 ms`) y el `p95` de DB se multiplica ~17.9x (`24.7 ms` -> `442.62 ms`).
2. **¿Dónde está el cuello de botella y por qué etapa ocurre?**
   Está concentrado en **`actividad.buscar.db`**, que representa **96.2%** de la latencia `p95` HTTP en alta concurrencia (`442.62 ms` sobre `460.32 ms`). Las trazas de Jaeger revelan la causa interna: aunque el `aggregate` (count) y el `find` inicial sobre `actividades` toman `< 1 ms` en reposo, el mapeo `ActividadMapper.toDomain` resuelve `@DBRef` sobre organizador y participantes generando **`~41` consultas secuenciales `find usuarios`** por cada página de 20 actividades (`47` spans por request en backend). Con `24` hilos equivale a **>4.300 operaciones/segundo** sobre MongoDB (`1.0` CPU) sin índices secundarios (`db.actividades.getIndexes()` solo contiene `_id_`), provocando `COLLSCAN` repetitivos (con picos de `87.07 ms` en `aggregate`) y contención de red/hilos.
3. **¿Cómo lo comprobamos en Grafana y Jaeger?**
   En **Grafana**, la curva `Duración p95 de accesos a MongoDB` acompaña casi 1:1 la subida de `Tiempo de respuesta p95 del flujo (HTTP)` (`442.62 ms` vs `460.32 ms`) mientras `Requests por segundo` se aplana y `% fallidos` permanece en `0%`. Al inspeccionar en **Jaeger** los trace IDs del Escenario 2 (`11658bb83b54164689613a919895824c`, `4efe9faf7a2b4dc224fbf6730e9a8e85`), el timeline muestra la cascada secuencial de 41 spans hijos `find usuarios` ocupando prácticamente todo el span `actividad.buscar.db`.
4. **¿Qué mejoraríamos?**
   - **Eliminar el patrón N+1 (`@DBRef`)**: reemplazar las referencias lazy en la búsqueda por una precarga en batch (`findAllById` con el conjunto único de IDs de usuarios de la página) o por proyección/desnormalización de resumen `{id, username}` (o `$lookup`), reduciendo `~41` roundtrips a `1`.
   - **Índices compuestos en MongoDB (`actividades`)**: indexar campos filtrados frecuentemente (`estado`, `tipo`, `fechaRealizacion`) para evitar `COLLSCAN`.
   - **Optimizar conteo (`aggregate`)**: evitar repetir el `count` cuando el resultado cabe en la página o cachear totales brevemente.
   - **Caché corta en validación JWT (`UsuarioMongoRepository.findById`)** para evitar una lectura extra por cada request de consulta.
5. **¿Cómo verificaríamos que la mejora funcionó?**
   Re-ejecutar `scripts/load-test-observabilidad.ps1` bajo las mismas cuotas (`1.5` CPU / `1.0` CPU, `300` actividades, `2` vs `24` hilos) y comprobar en **Grafana** que con `24` hilos aumentan los `req/s` y caen significativamente `p95 DB` y `p95 HTTP`, y en **Jaeger** que `actividad.buscar.db` pasa de `47` spans (`find usuarios x41`) a ~`3–4` spans sin cascada secuencial.

### Tag de Entrega

Al finalizar la revisión en la rama principal, crear y publicar el tag solicitado:

```bash
git tag Entrega_05
git push origin Entrega_05
```

## Uso de IA

En el desarrollo de este trabajo práctico se adoptó un enfoque de **Pair-Programming guiado por Inteligencia Artificial**, utilizando asistentes integrados al entorno de desarrollo.

### Herramientas y Modelos
- **Asistente / UI**: IDE con integración de agentes conversacionales (Antigravity) y Claude / v0 para prototipado rápido de componentes frontend.
- **Modelos**: Familia de modelos **Gemini** (Google) + **Sonnet 5** (Anthropic), utilizados por su gran capacidad de contexto para leer el código base completo de Spring Boot, diseñar la instrumentación OpenTelemetry (agente Java + API manual + Web SDK), generar el dashboard aprovisionado de Grafana, el script de pruebas de carga y los componentes en React/Tailwind.
- **CLI / Harness**: La IA interactuó nativamente con la terminal del sistema para ejecutar comandos de construcción, pruebas y verificación en contenedores (`./mvnw test`, `npm run build`, `docker compose`, consultas a Jaeger/Prometheus/Grafana), además de leer y editar archivos en tiempo real.

## Decisiones de Arquitectura y Diseño

1. **Separación de Capas**: 
   La aplicación respeta una arquitectura de capas bien definida: `controllers` -> `services` -> `repositories` -> `domain`.

2. **Modelo de Dominio Rico**:
   Las entidades (`Actividad`, `RangoReprogramacion`, `ReglasClima`) tienen métodos que encapsulan su propia lógica de negocio y validación de invariantes, delegando en los servicios únicamente la orquestación.

3. **Manejo Centralizado de Errores**:
   No se capturan (`catch`) excepciones directamente en los Controladores. En su lugar, los servicios y entidades lanzan excepciones propias (`AccesoDenegadoException`, etc.) que son procesadas transparentemente por un **`GlobalExceptionHandler`** (`@ControllerAdvice`).

4. **Inversión de Dependencias y Resiliencia (Testing)**:
   La llamada a los proveedores de pronósticos está abstraída por la interfaz `ProveedorClima` y orquestada por `SelectorProveedorClima`, permitiendo inyectar Mocks para correr tests de forma determinista y sin depender de internet ni del binario legacy.

5. **Desacoplamiento de Observabilidad**:
   La lógica de trazas y métricas en `ObservabilidadBusquedas` depende exclusivamente de `opentelemetry-api`, permitiendo ejecutar el suite de tests con `OpenTelemetry.noop()` o SDK en memoria sin agente ni Collector activos, mientras que el despliegue en contenedores delega exportación y almacenamiento al **OpenTelemetry Collector**.

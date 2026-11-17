# Reglas del Backend — Java 21 / Spring Boot (`backend/`)

Eres un desarrollador Backend experto en **Java 21** y **Spring Boot 4** (`com.tacs.backend`). Estas reglas complementan al [AGENTS.md raíz](../AGENTS.md) cuando trabajes dentro de `backend/`.

## Idioma, Formato e Imports (Java)

- **Idioma en español:** Todo código Java nuevo —nombres de clases, variables, métodos, parámetros, tests, Javadoc y comentarios— debe escribirse en **español**. Respetá los identificadores existentes en inglés si forman parte de una interfaz o contrato ya establecido hasta que se refactoricen de forma explícita.
- **Llaves estilo Allman:** Colocá `{` en la línea siguiente debajo de declaraciones de clase, método, `if`, `else`, `for`, `while`, etc. Si un `if` o `for` contiene una única sentencia simple, omití las llaves (manteniendo coherencia con el código existente del backend).
- **Prohibición de FQNs (Fully Qualified Names):** Nunca utilices nombres completamente calificados dentro del cuerpo del código (ej. prohibido `java.util.List<String>` o `com.tacs.backend.exceptions.ActividadNotFoundException`). Usá siempre el nombre simple de la clase e importala arriba.
- **Imports explícitos (sin wildcards):** Toda clase externa usada en un archivo debe declararse explícitamente en la sección de `import` en la cabecera. Prohibido usar wildcards (`import java.util.*;`, `import org.springframework.web.bind.annotation.*;`). Si agregás una clase nueva al editar un método, actualizá en el mismo paso el bloque de `import` en la cabecera del archivo.
- **Spotless:** `backend/pom.xml` configura `spotless-maven-plugin` (`removeUnusedImports`, `trimTrailingWhitespace`, `endWithNewline`). Asegurá que los archivos modificados cumplan con esas reglas.

## Arquitectura y Reglas de Diseño

- **Separación estricta en capas:** `controllers` -> `services` (interfaz + `implem`) -> `repositories` (interfaces de dominio) -> `domain`.
- **Dominio rico y agnóstico de persistencia:** Las clases en `domain/` son Java puro sin anotaciones de Spring Data MongoDB. La persistencia vive aislada en `persistence/` (`entities`, `mappers`, `repositories` adaptadores con `@DocumentReference`, `@Version` y `MongoTemplate`).
- **Mappers tontos:** Los mappers (`mappers/` y `persistence/mappers/`) solo transforman datos; nunca contienen reglas de negocio ni validaciones de dominio (ver ADR 4 en `docs/ADRs.md`).
- **Manejo centralizado de errores:** Lanzá excepciones de negocio propias (`com.tacs.backend.exceptions.*`). No captures excepciones (`try/catch`) en los controllers; dejá que `GlobalExceptionHandler` (`@ControllerAdvice` extendiendo `ResponseEntityExceptionHandler`) las convierta en `ProblemDetail` (RFC 7807). Evitá lógica basada en string-matching sobre mensajes de excepciones genéricas.
- **Concurrencia y consistencia (MongoDB):** Las operaciones con contención (`agregarParticipante`, `removerParticipante`, `registrarVoto`, resolución/cierre) se protegen con `@Version` y `OptimisticLockingRetry` (ADR 8). MongoDB standalone no soporta `@Transactional` multidocumento (ADR 6): persistí cambios críticos de estado antes de disparar notificaciones externas.
- **Zona horaria:** `TimeConfig` configura `TimeZone.setDefault` a `America/Argentina/Buenos_Aires` (`app.timezone`, ADR 9). No conviertas `LocalDateTime` locales asumiendo `ZoneOffset.UTC`.
- **Resiliencia e integraciones externas:** Al tocar proveedores de clima (`WeatherApiProveedorClima`, `LegacyProveedorClima`, `SelectorProveedorClima`) o Telegram, consultá `docs/ADRs.md` (ADRs 7, 11, 12) y `docs/proveedor-clima-legacy.md` para preservar validación estricta, orden de decorators Resilience4j, caché normalizada y fallback.
- **Observabilidad:** La instrumentación manual (`ObservabilidadBusquedas`) usa exclusivamente `opentelemetry-api` (sin acoplar código productivo a `opentelemetry-sdk`) y registra únicamente atributos acotados de baja cardinalidad (cero PII, cero texto libre, cero IDs).

## Testing en Backend

- **Nomenclatura y stack:** Escribí tests con JUnit 5, Mockito y AssertJ siguiendo el patrón `metodo_escenario_resultadoEsperado` (acompañado de `@DisplayName` descriptivo en español).
- **Aislamiento de servicios externos:** Mockeá siempre `ProveedorClima`, llamadas a WeatherAPI/Legacy Clima y API de Telegram en tests unitarios.
- **Tests por capa:**
  - Dominio y servicios: unitarios puros con JUnit 5 + Mockito.
  - Capa HTTP y seguridad: `@WebMvcTest` + `@MockitoBean` + `MockMvc` (validando `401`, `403`, `400 ProblemDetail`, `404`, `409`).
  - Observabilidad: `OpenTelemetrySdk` + `InMemorySpanExporter` + `InMemoryMetricReader` (ver `ObservabilidadBusquedasTest`).
  - Integración / concurrencia (`@DataMongoTest`): tené en cuenta que `TestMongoFallbackConfig` reemplaza los repositorios con un store en memoria cuando no hay MongoDB en `localhost:27017`, lo que puede ocultar errores reales de índices o queries. Validá cambios de persistencia/concurrencia contra un MongoDB real (`docker compose up -d mongodb`).
- **Variables de entorno en contexto Spring:** `application.properties` declara `${JWT_SECRET:?...}`, `${WEATHERAPI_API_KEY:?...}`, `${TELEGRAM_BOT_TOKEN:?...}` y `${TELEGRAM_BOT_USERNAME:?...}` (Spring interpreta el texto tras `:` como default literal y `JWT_SECRET` falla si tiene menos de 32 bytes). En `@SpringBootTest` definí propiedades explícitas o exportá variables dummy válidas (como hace `.github/workflows/ci.yml`).

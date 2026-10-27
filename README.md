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

### 2. Ejecutar la Aplicación (Docker)

Para iniciar todos los servicios junto con su red interna aislada y su base de datos:

```bash
docker compose up --build -d
```
*(Usamos `--build` para asegurarnos de que las imágenes se recompilen con los últimos cambios en el código, y `-d` para que corran en segundo plano).*

Esto levantará:
- El **Backend** (Spring Boot) en el puerto `8080`.
- El servicio **Clima Legacy** (`clima-legacy`) accesible únicamente dentro de la red interna de Docker en `http://clima-legacy:9978`, con supervisión y reinicio automático ante fallos.
- Una instancia de **MongoDB** en el puerto `27017`.
- El **Frontend** (React + Vite, servido con Nginx) en el puerto `80`. Podés acceder a la UI ingresando a **http://localhost** en tu navegador.

> **Documentación de Entrega 4 (Proveedor de Clima Legacy):** Todos los detalles de arquitectura, modos de falla descubiertos mediante ingeniería inversa del binario, decisiones de resiliencia (ADRs), diagrama de despliegue y resultados de las pruebas de carga se encuentran documentados en [`docs/proveedor-clima-legacy.md`](docs/proveedor-clima-legacy.md).

### 3. Documentación de la API (OpenAPI / Swagger)

Una vez que el contenedor esté corriendo, podés acceder a la interfaz gráfica de Swagger en:
**http://localhost:8080/swagger-ui.html** o a la especificación en JSON en **http://localhost:8080/v3/api-docs**.

### 4. Ejecutar los Tests Locales

Para correr la suite desarrollada con JUnit y Mockito (que incluye pruebas unitarias de toda la lógica de resiliencia, validación, caché y fallback sin depender del binario real ni de APIs externas):

```bash
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

## Uso de IA

En el desarrollo de este trabajo práctico se adoptó un enfoque de **Pair-Programming guiado por Inteligencia Artificial**, utilizando asistentes integrados al entorno de desarrollo.

### Herramientas y Modelos
- **Asistente / UI**: IDE con integración de agentes conversacionales (Antigravity) y Claude / v0 para prototipado rápido de componentes frontend.
- **Modelos**: Familia de modelos **Gemini** (Google) + **Sonnet 5** (Anthropic), utilizados por su gran capacidad de contexto para leer el código base completo de Spring Boot, así como para generar los componentes funcionales en React/Tailwind.
- **CLI / Harness**: La IA interactuó nativamente con la terminal del sistema para ejecutar comandos de construcción y pruebas (`./mvnw test`, `npm run build`), además de leer y parchear archivos en tiempo real y resolver conflictos de merges.

## Decisiones de Arquitectura y Diseño

1. **Separación de Capas**: 
   La aplicación respeta una arquitectura de capas bien definida: `controllers` -> `services` -> `repositories` -> `domain`.

2. **Modelo de Dominio Rico**:
   Las entidades (`Actividad`, `RangoReprogramacion`, `ReglasClima`) tienen métodos que encapsulan su propia lógica de negocio y validación de invariantes, delegando en los servicios únicamente la orquestación.

3. **Manejo Centralizado de Errores**:
   No se capturan (`catch`) excepciones directamente en los Controladores. En su lugar, los servicios y entidades lanzan excepciones propias (`AccesoDenegadoException`, etc.) que son procesadas transparentemente por un **`GlobalExceptionHandler`** (`@ControllerAdvice`).

4. **Inversión de Dependencias y Resiliencia (Testing)**:
   La llamada a los proveedores de pronósticos está abstraída por la interfaz `ProveedorClima` y orquestada por `SelectorProveedorClima`, permitiendo inyectar Mocks para correr tests de forma determinista y sin depender de internet ni del binario legacy.

# TACS - Trabajo Práctico

Este repositorio contiene el backend del trabajo práctico para la materia **TACS** (Tecnologías Avanzadas en la Construcción de Software), desarrollado en **Java 21** con **Spring Boot**.

El sistema permite organizar actividades (tanto en espacios cerrados como al aire libre), gestionar participantes, chequear las condiciones climáticas de forma periódica, e iniciar votaciones de reprogramación (con quórum) en caso de que el clima sea desfavorable o el organizador deba cancelar el evento.

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

### 2. Ejecutar la Aplicación (Docker)

Para iniciar el backend junto con su red de forma aislada, tal como exige la rúbrica:

```bash
docker compose up --build -d
```
*(Usamos `--build` para asegurarnos de que la imagen se recompile con los últimos cambios en el código, y `-d` para que corra en segundo plano).*

Esto levantará la aplicación en el puerto `8080`. 

> **Nota sobre Base de Datos (Entrega 1):** Según los requerimientos de la primera entrega, el modelo funciona *en memoria*. Para facilitar el desarrollo y algunas consultas manuales temporales se incluyó **H2 Database**. En la Entrega 2, esto será reemplazado por persistencia real en una base de datos NoSQL.

### 3. Documentación de la API (OpenAPI / Swagger)

El esqueleto de la aplicación expone automáticamente sus rutas REST documentadas, como es recomendado para esta entrega.
Una vez que el contenedor esté corriendo, podés acceder a la interfaz gráfica de Swagger en:
**http://localhost:8080/swagger-ui.html** o a la especificación en JSON en **http://localhost:8080/v3/api-docs**.

### 4. Ejecutar los Tests Locales

Los tests son fundamentales (y obligatorios según rúbrica). Para correr la suite desarrollada con JUnit y Mockito (que evita llamadas a la API externa de clima):

```bash
cd backend
./mvnw test
```

## Uso de IA

En el desarrollo de este trabajo práctico se adoptó un enfoque de **Pair-Programming guiado por Inteligencia Artificial**, utilizando asistentes integrados al entorno de desarrollo.

### Herramientas y Modelos
- **Asistente / UI**: IDE con integración de agentes conversacionales (Antigravity) y Claude.
- **Modelos**: Familia de modelos **Gemini** (Google) + **Sonnet 5** (Anthropic), utilizados por su gran capacidad de contexto para leer el código base completo de Spring Boot.
- **CLI / Harness**: La IA interactuó nativamente con la terminal del sistema para ejecutar comandos de construcción y pruebas (`./mvnw test`), además de leer y parchear archivos en tiempo real.

## Decisiones de Arquitectura y Diseño

1. **Separación de Capas**: 
   La aplicación respeta una arquitectura de capas bien definida: `controllers` -> `services` -> `repositories` -> `domain`.

2. **Modelo de Dominio Rico**:
   Las entidades (`Actividad`, `RangoReprogramacion`, `ReglasClima`) tienen métodos que encapsulan su propia lógica de negocio y validación de invariantes, delegando en los servicios únicamente la orquestación.

3. **Manejo Centralizado de Errores**:
   No se capturan (`catch`) excepciones directamente en los Controladores. En su lugar, los servicios y entidades lanzan excepciones propias (`AccesoDenegadoException`, etc.) que son procesadas transparentemente por un **`GlobalExceptionHandler`** (`@ControllerAdvice`).

4. **Inversión de Dependencias (Testing)**:
   La llamada a la API de pronósticos está abstraída por la interfaz `ProveedorClima`, permitiendo inyectar Mocks (Mockito) para correr tests de forma determinista y sin depender de internet.
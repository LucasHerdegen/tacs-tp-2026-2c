# Ejemplos de Prompts (Setup de IA)

Durante el desarrollo, utilizamos el asistente de inteligencia artificial y modelos como Claude / ChatGPT / Gemini para acelerar ciertas implementaciones repetitivas, generar casos de test y ayudarnos con refactors.

A continuación, documentamos los prompts clave que fueron utilizados:

## 1. Generación de Base de Dominio

**Prompt utilizado:**
> "Estamos diseñando el backend para una aplicación de TACS en Spring Boot 3 con Java 21. El dominio principal trata de 'Actividades' que pueden realizarse al 'AIRE_LIBRE' o 'TECHADA'. Crea la clase Actividad, que tenga titulo, descripcion, ubicacion, fecha de realizacion y un rango de reprogramacion. También crea un enum para el EstadoActividad (Propuesta, Confirmada, Reprogramada, Cancelada, Finalizada). Las reglas de negocio indican que si el estado es CONFIRMADA y el clima empeora, se debe poder abrir una votacion. Solo genera las clases planas sin JPA por el momento."

## 2. Implementación del Job de Chequeo de Clima (Scheduler)

**Prompt utilizado:**
> "Necesito implementar una tarea programada (@Scheduled) en Spring Boot que corra cada 12 horas. Esta tarea debe buscar todas las actividades en estado CONFIRMADA o PROPUESTA cuya fecha de realización sea en las próximas 48 hs. Para cada una, debe consultar el clima usando una interfaz ProveedorClima. Si el clima no cumple con las ReglasClima configuradas para la actividad, debe lanzar una Votacion automática notificando a los participantes. Generame el código del Job y el servicio asociado inyectando las dependencias necesarias por constructor usando Lombok."

## 3. Generación de Tests de Integración y MockMvc

**Prompt utilizado:**
> "Tenemos un archivo VotacionesIntegrationTests.java que actualmente levanta un servidor real en un RANDOM_PORT y hace llamadas HTTP usando HttpClient nativo de Java. Sin embargo, los tests están ensuciando la base de datos entre clases. Refactoriza todo el test para utilizar MockMvc (@AutoConfigureMockMvc) con @Transactional, de forma tal que cada @Test ejecute un rollback de la base de datos H2 en memoria al finalizar. Asegurate de mantener todos los assertions usando AssertJ y adaptar el flujo de Autenticación de JWT (haciendo post al endpoint de login para recuperar el token y mandarlo en el header Authorization de las llamadas)."

## 4. Estrategia de Dispatcher Pattern

**Prompt utilizado:**
> "Queremos mandar notificaciones a los usuarios cuando una actividad se cancela. Algunos usuarios prefieren Email y otros Telegram. Diseña una solución escalable en Spring usando el patrón de diseño Dispatcher, donde tengamos una interfaz Notificador y un NotificadorDispatcher que se encargue de iterar sobre una colección de beans que implementan Notificador. El Notificador debe tener un método boolean soporta(TipoContacto tipo)."

# Ejemplos de Prompts y Uso de IA

Durante el desarrollo, utilizamos el asistente de inteligencia artificial y modelos como Claude / ChatGPT / Gemini para acelerar ciertas implementaciones repetitivas, generar casos de test y ayudarnos con refactors.

A continuación, documentamos los prompts clave que fueron utilizados:

## 1. Generación de Base de Dominio
**Contexto**: Modelado inicial sin acoplar a base de datos.
**Prompt utilizado:**
> "Estamos diseñando el backend para una aplicación de TACS en Spring Boot 3 con Java 21. El dominio principal trata de 'Actividades' que pueden realizarse al 'AIRE_LIBRE' o 'TECHADA'. Crea la clase Actividad, que tenga titulo, descripcion, ubicacion, fecha de realizacion y un rango de reprogramacion. También crea un enum para el EstadoActividad (Propuesta, Confirmada, Reprogramada, Cancelada, Finalizada). Las reglas de negocio indican que si el estado es CONFIRMADA y el clima empeora, se debe poder abrir una votacion. Solo genera las clases planas sin JPA por el momento."

## 2. Implementación del Job de Chequeo de Clima (Scheduler)
**Contexto**: Tareas en background programadas.
**Prompt utilizado:**
> "Necesito implementar una tarea programada (@Scheduled) en Spring Boot que corra cada 12 horas. Esta tarea debe buscar todas las actividades en estado CONFIRMADA o PROPUESTA cuya fecha de realización sea en las próximas 48 hs. Para cada una, debe consultar el clima usando una interfaz ProveedorClima. Si el clima no cumple con las ReglasClima configuradas para la actividad, debe lanzar una Votacion automática notificando a los participantes. Generame el código del Job y el servicio asociado inyectando las dependencias necesarias por constructor usando Lombok."

## 3. Generación de Tests de Integración y MockMvc
**Contexto**: Aislamiento en tests para la BD H2 en memoria.
**Prompt utilizado:**
> "Los tests ActividadesMeIntegrationTests y EstadisticasIntegrationTests fallan en el setUp con 'Referential integrity constraint violation: VOTACION FOREIGN KEY(ACTIVIDAD_ID) REFERENCES ACTIVIDAD(ID)'. Pareciera que actividadesRepository.deleteAll() rompe porque quedan Votaciones colgadas de otros tests. Por favor migrá estas suites a MockMvc si es necesario para ganar aislamiento transaccional y eliminá la limpieza manual que está ensuciando la base."

## 4. Estrategia de Dispatcher Pattern para Notificaciones
**Contexto**: Flexibilidad de canales de notificación.
**Prompt utilizado:**
> "Queremos mandar notificaciones a los usuarios cuando una actividad se cancela. Algunos usuarios prefieren Email y otros Telegram. Diseña una solución escalable en Spring usando el patrón de diseño Dispatcher, donde tengamos una interfaz Notificador y un NotificadorDispatcher que se encargue de iterar sobre una colección de beans que implementan Notificador. El Notificador debe tener un método boolean soporta(TipoContacto tipo)."

## 5. Desacople JPA del modelo de Dominio
**Contexto**: Las clases de negocio tenían la lógica mezclada con `@Entity`.
**Prompt utilizado:**
> "Por favor creá un modelo de entidades paralelas en persistence/entities terminadas en Entity (ej: ActividadEntity, UsuarioEntity). Remové todas las importaciones de jakarta.persistence.* del modelo de dominio, construí RepositoriesWrapper que implementen nuestras interfaces de dominio pero llamen por dentro a interfaces JpaRepository de Spring. Proveé los mappers necesarios entre ambas capas."

## 6. Concurrencia y Shedlock
**Contexto**: Problemas al guardar participantes concurrentes y ejecución duplicada de Cron Jobs.
**Prompt utilizado:**
> "Noté que dos request concurrentes para anotarse a una actividad exceden el cupo o duplican entradas. Agregá control de concurrencia usando Optimistic Locking (@Version) en la Actividad y una constraint UNIQUE en la join table. Además agregá ShedLock a los trabajos programados para asegurar que no se ejecuten múltiples veces si escalamos la app horizontalmente."

## 7. Manejo Consistente de Errores (Problem Details)
**Contexto**: Estandarización de errores 4xx y 5xx.
**Prompt utilizado:**
> "Modificá el GlobalExceptionHandler para que atrape IllegalArgumentException, IllegalStateException, MethodArgumentNotValidException y devuelva un formato estandarizado usando ProblemDetail RFC 7807, con los HTTP status code correctos (400, 404 o 409 según el mensaje/contexto) y devolviendo en un mapa los campos fallidos en las validaciones de bean."
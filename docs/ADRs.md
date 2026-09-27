# Architecture Decision Records (ADRs)

En este documento se registran las decisiones de arquitectura más importantes tomadas durante el desarrollo.

## ADR 1: Separación estricta entre Dominio y Persistencia (JPA)

**Contexto**: En las primeras iteraciones, el modelo de dominio contenía anotaciones propias de JPA (`@Entity`, `@ManyToMany`, `@Embeddable`, etc.). Dado que el objetivo arquitectónico a futuro es poder migrar de una base relacional a una NoSQL (ej. MongoDB) de forma transparente, tener la capa de dominio acoplada a las dependencias y restricciones semánticas de JPA limitaría esa capacidad. 

**Decisión**: Se decidió implementar un patrón de **Repository Wrapper y Mappers**:
1. Limpiar completamente las clases en `com.tacs.backend.domain` de cualquier referencia a `jakarta.persistence.*`.
2. Crear un modelo paralelo de entidades (terminadas en `Entity`) dentro del paquete `persistence.entities`.
3. Implementar las interfaces de repositorio del dominio utilizando una clase adaptadora que por dentro delega en `Spring Data JPA`.
4. Proveer Mappers bidireccionales que conviertan entre el modelo de Dominio puro y el modelo `Entity`.

**Consecuencias**: La capa de dominio queda completamente agnóstica del mecanismo de persistencia. Sin embargo, aumenta la verbosidad y la cantidad de clases a mantener.


## ADR 2: Estrategia de Quórum y Evaluación por Alternativa

**Contexto**: Cuando el clima no es favorable o el organizador decide posponer un evento, se abre una votación. Necesitamos una regla clara para saber cuándo una reprogramación es válida, y cómo interpretar el "quórum mínimo".

**Decisión**: 
1. El `quorumMinimo` de una votación automática es exactamente igual al `minimoParticipantes` configurado en la Actividad original.
2. Se implementa el **Quórum por alternativa**: Los votos específicos que recibió una alternativa en particular deben ser `>= quorumMinimo` para que esa alternativa sea considerada ganadora, en vez de evaluar la participación general de la votación. Esto obedece a la consigna: *"que se cancele si ninguna alternativa alcanza el quórum mínimo"*.

**Consecuencias**: Garantizamos que el evento reprogramado siga siendo viable. La implementación está estrictamente alineada a la lectura literal de la consigna.


## ADR 3: Alcance y Uso de Telegram como Notificador

**Contexto**: El requerimiento original mencionaba a Telegram. En las primeras entregas, la integración de Telegram fue abordada y modelada bajo el concepto de un `Notificador` (es decir, una estrategia de canal de salida para enviar avisos como alertas climáticas o recordatorios). Las devoluciones sugirieron que Telegram podría ser una interfaz navegable de entrada.

**Decisión**: Se mantiene la integración modelada como notificador (`MedioContacto`) y se consolida un `NotificacionDispatcher` que inyecta las implementaciones. La construcción de la API REST fue priorizada como interfaz genérica, y aún no se ha acoplado un framework de Webhooks/Polling que reciba los inputs del bot. La API expone todos los endpoints necesarios para que el desarrollo del bot pueda abstraerse y actuar como un cliente consumidor más de nuestra capa de servicios en una entrega futura.

**Consecuencias**: La API REST queda independiente de Telegram. El desarrollo del bot podrá realizarse consumiendo las interfaces sin inyectar librerías específicas (e.g. `TelegramBots`) dentro del core del dominio.


## ADR 4: Validaciones Delegadas en el Dominio vs Mappers

**Contexto**: Durante la implementación de la capa de API y Mappers, surgió la necesidad de definir dónde reside la validación del estado de las Actividades para ciertas acciones (e.g., crear una votación nueva). Inicialmente existía lógica validatoria dispersa en los Mappers.

**Decisión**: Se decide que los Mappers (`*Mapper`) son clases tontas de infraestructura y no deben contener lógica de negocio, ni lanzar excepciones sobre la consistencia de los datos. Toda regla de negocio (restricciones de fecha, validación de estado de actividad) debe validarse dentro de la capa de Dominio o, en su defecto, en la orquestación de los `Services`.

**Consecuencias**: La validación queda encapsulada donde corresponde (Alta cohesión). Las capas de API quedan liberadas de conocer las reglas y solo se encargan de enrutar excepciones de dominio a errores HTTP a través del `GlobalExceptionHandler`.


## ADR 5: Migracion de Base de Datos Relacional a MongoDB

**Contexto**: Como parte de los requisitos de la Entrega 2, el sistema debe persistir los datos utilizando una base de datos NoSQL. Originalmente se utilizaba H2 en memoria con Spring Data JPA.

**Decision**: 
1. Migrar la capa de persistencia de Spring Data JPA a Spring Data MongoDB.
2. Cambiar los identificadores (\id\) numericos autoincrementales (\Long\) por el estandar \ObjectId\ (\String\) de MongoDB en todo el sistema (Entidades, Dominio, DTOs).
3. Aprovechar el mapeo orientado a documentos, embebiendo entidades dependientes (como \AlternativaEntity\ y \VotoEntity\ en \VotacionEntity\) y utilizando referencias (\@DocumentReference\) para relaciones independientes (como \UsuarioEntity\ en \ActividadEntity\).
4. Reemplazar \JdbcTemplateLockProvider\ por \MongoLockProvider\ para que ShedLock siga funcionando sobre MongoDB.

**Consecuencias**: El sistema pasa a estar puramente basado en documentos, mejorando el alineamiento con el paradigma NoSQL. Las busquedas complejas (que antes utilizaban JOINs en JPA) ahora se resuelven de forma mas nativa o a traves de filtrado en aplicacion para evitar \$lookup\ excesivos, respetando el modelo NoSQL.

## ADR 6: Eliminacion de transacciones en MongoDB

**Contexto**: Durante el desarrollo en base de datos relacional (JPA), se utilizaban anotaciones @Transactional para garantizar la consistencia en escrituras multiples. Con la migracion a MongoDB, dado que no hay un MongoTransactionManager configurado y el cluster local provisto en el docker-compose no es un replica set (prerrequisito obligatorio de MongoDB para las transacciones multidocumento), las anotaciones perdieron efecto real.

**Decision**: 
1. Eliminar por completo todas las anotaciones @Transactional de la capa de Servicios y los Jobs (Cron).
2. Documentar que las escrituras multidocumento (e.g. esolverVotacion que guarda la actividad y la votacion) ahora son eventualmente consistentes y se ejecutan como escrituras independientes, en lugar de intentar forzar el motor transaccional de Spring Data MongoDB.

**Consecuencias**: El codigo refleja fielmente la semantica actual de almacenamiento (que no goza de garantias ACID multidocumento). Para habilitar verdaderas transacciones a futuro, requeriria reconfigurar el compose.yaml a replica set y definir el bean del manejador transaccional.

## ADR 7: Telegram como interfaz completa de entrada (no solo Notificador)

**Contexto**: La corrección de la Entrega 1 aclaró que la integración con Telegram pedida en la consigna no es únicamente el `Notificador` (canal de salida) descripto en el ADR 3 — es una interfaz alternativa al frontend, con comandos y botones para crear actividades, buscarlas, sumarse, votar y ver estado. Había que definir con qué librería construirla, cómo recibir los mensajes, y cómo resolver la identidad del usuario sin depender del JWT.

**Decisión**:
1. **Librería cliente: `com.github.pengrad:java-telegram-bot-api`**, en vez de `org.telegram:telegrambots` (framework más pesado, con su propio modelo de hilos y una migración de API reciente que generó fragmentación) o de armar los DTOs de la Bot API a mano con `RestClient` (válido solo para "mandar un mensaje"; con botones inline y estado conversacional es reinventar la rueda). Pengrad da tipos para `SendMessage`, `InlineKeyboardMarkup`, `CallbackQuery`, `GetUpdates`, etc., sin imponer un ciclo de vida propio — fácil de envolver en beans de Spring.
2. **Long polling, no webhook**: el backend pregunta periódicamente por novedades (`GetUpdates` con offset persistido) en vez de que Telegram le pegue a una URL pública. No requiere HTTPS público y funciona igual en local (`docker-compose`) que en la nube.
3. **Alta nativa sin contraseña**: si alguien le escribe al bot sin un token de vinculación y sin cuenta previa, se crea un `Usuario` nuevo ahí mismo usando el `chat_id` como identidad. Coherente con la consigna ("no es objetivo del TP trabajar sobre autenticación") y hace que Telegram sea una interfaz autónoma, no dependiente del frontend.
4. **Identidad por `chat_id`, no por JWT**: los handlers resuelven `chat_id → Usuario` contra `MedioContacto` y llaman a los mismos `Service` que ya usan los controllers REST, pasando el `usuarioId` explícito — cero lógica de negocio duplicada.

**Consecuencias**: La interfaz de Telegram reutiliza el mismo dominio y los mismos `Service` que el REST, sin duplicar reglas. El principal trade-off conocido es que `GetUpdates` solo admite **un consumidor concurrente por token**: si se escalara horizontalmente el backend, una segunda instancia haciendo polling recibiría `409 Conflict` y desplazaría a la anterior. Para este TP se documenta como aceptado (single-instance); si se necesitara escalar, la solución sería usar ShedLock (ya presente en el proyecto) para elegir una única instancia "dueña" del poller, o migrar a webhook.

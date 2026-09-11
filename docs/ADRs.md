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

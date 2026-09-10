# Architecture Decision Records (ADRs)

En este documento se registran las decisiones de arquitectura más importantes tomadas durante el desarrollo.

## ADR 1: Estrategia de Quorum para Votaciones de Reprogramación

**Contexto**: Cuando el clima no es favorable o el organizador decide posponer un evento, se abre una votación para elegir una nueva fecha. Necesitamos una regla clara para saber cuándo una reprogramación es válida.
**Decisión**: Se estableció que el `quorumMinimo` de una votación debe ser exactamente igual al `minimoParticipantes` configurado en la Actividad original. Si una alternativa (fecha) alcanza esa cantidad de votos, la actividad se reprograma.
**Justificación**: Si un evento requería al menos N personas para llevarse a cabo (por ejemplo, un partido de fútbol 5 requiere 10 personas), reprogramarlo con una asistencia menor carece de sentido. Garantizamos que el evento reprogramado siga siendo viable.
**Consecuencias**: Las votaciones que no logran reunir suficientes adeptos podrían expirar, obligando a tener Jobs que cancelen o finalicen las votaciones y actividades abandonadas.

## ADR 2: Uso de Telegram como canal principal de notificaciones

**Contexto**: El sistema debe enviar alertas y notificaciones a los participantes cuando ocurren eventos importantes (creación de votación, confirmación, cancelación).
**Decisión**: Se optó por una abstracción `Notificador` apoyada por un `NotificacionDispatcher` que inyecta todas las implementaciones disponibles. La implementación principal es `Telegram`.
**Justificación**: Telegram fue elegido porque su API de bots es sencilla, gratuita y requiere menos fricción para los usuarios (que proveen su medio de contacto en el perfil). La arquitectura de abstracción permite que, si en el futuro queremos agregar Email o SMS, simplemente creamos una clase que implemente `Notificador` y el dispatcher la tomará automáticamente según el tipo de contacto.
**Consecuencias**: Dependemos de la disponibilidad de la API de Telegram y debemos inyectar de forma segura el bot token mediante variables de entorno (`.env`).

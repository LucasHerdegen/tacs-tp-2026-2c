# Skill: Confección de Plan de Implementación

## Propósito
Evitar la generación de código apresurado. Asegurar que antes de escribir código, se validen las opciones arquitectónicas, se discutan las dependencias y se alineen las expectativas de implementación.

## Cuándo activar esta Skill
- **SIEMPRE** que el usuario solicite una nueva funcionalidad, un nuevo componente, o una integración entre servicios.
- Se debe activar **incluso si el usuario sugiere una solución técnica específica**, a menos que el usuario indique explícitamente "saltar el plan" o "generar código directo".

## Instrucciones paso a paso
1. **Pausa de Código:** Tienes estrictamente prohibido generar código fuente (Java, XML, YAML) en esta etapa. Tu única salida debe ser el plan.
2. **Inspeccionar el proyecto:** Analiza el contexto de DonaTrack, la estructura de microservicios involucrados y el modelo de dominio.
3. **Análisis de opciones:** Si el usuario propuso una solución, evalúala críticamente (¿es la mejor opción en Spring Boot?). Si no propuso ninguna, presenta 2 o 3 alternativas viables detallando pros y contras de cada una (ej. rendimiento, acoplamiento, escalabilidad).
4. **Resolver dudas:** Identifica "puntos ciegos" en la petición. ¿Faltan librerías? ¿Cómo se manejan los errores? ¿Hay dependencias con otros microservicios (ej. Autenticación y Donaciones)?

## Formato de Reporte
Responde estrictamente con este formato:
- **Evaluación del enfoque:** (Breve análisis de lo que pidió el usuario).
- **Lista de opciones:** (Opciones arquitectónicas con pros/contras).
- **Preguntas de diseño:** (Dudas clave a resolver antes de codificar).
- **Recomendación del Maestro:** (Tu sugerencia profesional sobre qué camino tomar).
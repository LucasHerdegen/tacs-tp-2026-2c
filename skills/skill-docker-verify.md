# Skill: Validación de Contenedores e Infraestructura

## Propósito
Garantizar que todos los servicios, bases de datos y herramientas de mensajería del entorno definido en el `docker-compose.yml` se construyan correctamente, se comuniquen en red y mantengan un estado estable (Healthy/Up).

## Cuándo activar esta Skill
- Al modificar el `Dockerfile`, el `docker-compose.yml` o las variables de entorno (`application.properties`/`application.yml`).
- Al agregar dependencias de infraestructura nuevas (ej. una nueva base de datos o broker).
- Cuando un servicio reporta errores de conexión a la base de datos o a otros servicios.

## Instrucciones paso a paso (desde el root)
1. **Validación de sintaxis:** Ejecutar `docker compose config` para asegurar que el archivo YAML no tenga errores de indentación o configuración.
2. **Construcción y despliegue:** Ejecutar `docker compose up -d --build`. Esto forzará la recompilación de las imágenes (clave por si hubo cambios en el código) y levantará los servicios en segundo plano.
3. **Tiempo de gracia:** Esperar entre 15 y 20 segundos. Los motores de bases de datos y las aplicaciones de Spring Boot tardan un momento en inicializar.
4. **Verificación de estado:** Ejecutar `docker compose ps`. Validar que todos los contenedores estén en estado "Up". Si alguno está en "Restarting" o "Exited", hay un fallo crítico.
5. **Análisis de logs en caso de fallo:** Si algún contenedor falló, ejecutar `docker compose logs <nombre_del_servicio>` (ej. `docker compose logs servicio-donaciones`). Analizar el error, corregir el código/configuración y volver al paso 2.
6. **Limpieza del entorno:** Una vez validado el funcionamiento integral, ejecutar `docker compose down -v` para detener los contenedores y eliminar los volúmenes efímeros, garantizando que la próxima prueba arranque limpia.

## Formato de Reporte
Al finalizar, muestra al usuario un resumen con este formato:
- **Estado de Infraestructura:** [Estable / Inestable]
- **Contenedores Activos:** [X de Y] servicios en estado Up.
- **Observaciones:** (Detallar si hubo algún contenedor reiniciándose, problemas de red o errores de conexión en los logs, y cómo se resolvieron).
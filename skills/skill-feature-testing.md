# Skill: Desarrollo y Testing Guiado por Comportamiento

## Propósito
Asegurar que toda nueva funcionalidad, endpoint REST o regla de negocio implementada en Spring Boot cuente con su correspondiente validación automatizada antes de darse por finalizada, previniendo regresiones en el sistema.

## Cuándo activar esta Skill
- Cada vez que el usuario solicite crear o modificar un caso de uso, un servicio (Service layer) o un controlador (Controller layer).

## Instrucciones paso a paso
1. **Análisis de Requerimientos:** Antes de escribir el código de producción, analiza qué se espera que haga la funcionalidad y cuáles son sus restricciones.
2. **Creación del Happy Path (Camino Feliz):** Diseña y escribe primero un test unitario en JUnit 5 que valide el escenario ideal. Por ejemplo, si es un Controller, un test que espere un HTTP 200 OK con un JSON válido.
3. **Creación del Failing Path (Caso de Fallo):** Escribe al menos un test unitario que fuerce un error predecible. Por ejemplo, enviar un DTO con datos nulos esperando un HTTP 400 Bad Request, o simular que la base de datos no encuentra un registro esperando un HTTP 404 Not Found.
4. **Implementación del Código:** Escribe el código de producción en Java/Spring Boot estrictamente necesario para que los tests del paso 2 y 3 pasen exitosamente.
5. **Ejecución y Verificación:** Ejecuta los tests unitarios de la clase modificada (`mvn test -Dtest=NombreDeLaClaseTest`).
6. **Refactorización:** Si los tests pasan en verde, revisa si el código de producción cumple con las métricas de calidad y formato del proyecto.

## Formato de Reporte
Al entregar el código, incluye una breve sección indicando:
- **Funcionalidad implementada:** [Breve descripción]
- **Casos de prueba creados:** [Mencionar el Happy Path y el/los Failing Paths cubiertos]
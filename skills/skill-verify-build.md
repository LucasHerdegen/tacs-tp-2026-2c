# Skill: Verificación de Build y Tests

## Propósito
Asegurar que cualquier cambio en el código mantenga la estabilidad del proyecto, compilando correctamente y superando la suite de pruebas automatizadas, optimizando el ciclo de vida de Maven.

## Cuándo activar esta Skill
- Inmediatamente después de generar o modificar código fuente.
- Antes de dar una tarea por finalizada o entregar una respuesta al usuario.

## Instrucciones paso a paso (todo desde el root)
1. **Ejecutar la compilación:** Utiliza el comando `mvn clean compile`. Esto limpia builds anteriores y verifica errores de sintaxis en el código principal.
2. **Analizar errores de compilación:** Si el build falla, detén el proceso, lee el log de errores, corrige el código y vuelve al paso 1.
3. **Ejecutar la suite de pruebas:** Una vez compilado, lanza los tests unitarios con `mvn test` (sin clean, para aprovechar la compilación previa).
4. **Analizar errores de tests:** Si los tests fallan, detén el proceso, lee el log de errores, corrige el código y vuelve al paso 1 (para asegurar que la corrección recompile).
5. **Ejecutar verificación integral:** Una vez pasados los tests, ejecuta `mvn verify` (sin clean). Esto empaquetará la aplicación y correrá tests de integración si los hubiera.
6. **Analizar errores de la verificación:** Si falla, detén el proceso, lee el log, corrige y vuelve al paso 1.

## Formato de Reporte
Al finalizar, muestra siempre al usuario un resumen con este formato:
- **Estado del Build:** [Exitoso / Fallido (en caso de requerir intervención humana)]
- **Tests Ejecutados:** [Número] pasados, [Número] fallados.
- **Acción tomada:** (Si algo falló, explica brevemente qué corregiste y por qué es la solución adecuada).
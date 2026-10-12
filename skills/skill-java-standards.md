# Skill: Estándares de Código Java y Manejo de Imports

## Propósito
Garantizar un código limpio, legible e idiomático en Java, evitando la verbosidad innecesaria y asegurando el correcto manejo de dependencias a nivel de archivo.

## Reglas Obligatorias
1. **Prohibición de FQNs (Fully Qualified Names):** Nunca utilices nombres completamente calificados dentro del cuerpo del código (ej. prohíbido usar `java.util.List<String>`). Utiliza siempre el nombre simple de la clase.
2. **Imports explícitos en la cabecera:** Toda clase externa debe ser declarada explícitamente en la sección de imports en la parte superior del archivo (ej. `import java.util.List;`).
3. **Prohibición de Wildcards:** No utilices importaciones con comodines (ej. `import java.util.*;`). Cada clase debe tener su propia línea de importación para evitar colisiones de nombres y hacer el código predecible.
4. **Regeneración de código (Cero atajos):** Si durante la escritura de un método determinas que necesitas una clase que no fue importada inicialmente, debes regenerar la respuesta completa para incluir el `import` en la cabecera. No utilices FQNs como solución rápida.
# Reglas del Proyecto TACS - Spring Boot

- Rol: Eres un desarrollador Backend experto en Java y Spring Boot. NO utilices scripts de Python o Bash para leer, escribir o modificar código; utiliza exclusivamente tus capacidades nativas de edición.
- Idioma: Todo el código, nombres de variables, métodos, comentarios y mensajes de commit DEBEN escribirse estrictamente en español.
- Formato: Las llaves deben colocarse en la línea siguiente (debajo del método, for, if, etc.). Además, si un if/for contiene una sola línea, se deben omitir las llaves.
- Arquitectura: Respeta la separación en capas (controllers, services, repositories, domain). Las entidades del dominio deben ser ricas y encapsular su propia lógica de negocio.
- Manejo de Errores: Lanza excepciones de negocio personalizadas. No captures excepciones en los controladores; deja que el GlobalExceptionHandler centralizado las intercepte y formatee la respuesta HTTP.
- Testing: Escribe tests unitarios para cada caso de uso utilizando JUnit y Mockito. Nombra los métodos siguiendo el patrón: `metodo_escenario_resultadoEsperado`. Mockea siempre las llamadas a la API del clima.
- Infraestructura y Seguridad: Las credenciales y API Keys jamás deben hardcodearse en el código. Lee siempre estos valores desde variables de entorno para mantener la compatibilidad con Docker.
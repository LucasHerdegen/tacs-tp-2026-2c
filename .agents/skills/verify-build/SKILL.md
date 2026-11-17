---
name: verify-build
description: >-
  Verifies backend compilation, Spotless formatting, and Maven tests (JUnit/Mockito),
  plus frontend ESLint and TypeScript/Vite build. Use after modifying source code or
  before completing any implementation task.
---

# Skill: Verificación de Build, Formato y Tests

## Propósito
Garantizar que cualquier cambio en `backend/` o `frontend/` compile sin errores, cumpla con las reglas de formato (`spotless` / `eslint`), supere la suite de tests y genere el bundle de producción correctamente.

## Cuándo activar esta Skill
- Inmediatamente después de crear o modificar código en `backend/` o `frontend/`.
- Antes de entregar una tarea o dar por finalizada una corrección.

## Opción rápida (script multiplataforma)

Podés ejecutar el runner unificado desde la raíz del repositorio:

```powershell
# Verifica backend (compile, test, spotless:check) y frontend (lint, build)
node .agents/skills/verify-build/scripts/verify.mjs

# O por módulo / filtro de test:
node .agents/skills/verify-build/scripts/verify.mjs --backend
node .agents/skills/verify-build/scripts/verify.mjs --frontend
node .agents/skills/verify-build/scripts/verify.mjs --backend --test ObservabilidadBusquedasTest
```

Ver [scripts/verify.mjs](./scripts/verify.mjs) para ver todas las opciones.

## Instrucciones paso a paso (manuales)

### 1. Backend (`backend/`)
Ejecutar siempre **desde el directorio `backend/`** (en Windows PowerShell usar `.\mvnw.cmd`; en Linux/macOS/Bash usar `./mvnw`):

1. **Compilación:**
   ```powershell
   .\mvnw.cmd clean compile
   ```
   Si falla, corregí los errores de compilación antes de continuar.

2. **Suite de tests:**
   ```powershell
   .\mvnw.cmd test
   ```
   - Para correr una clase puntual: `.\mvnw.cmd test -Dtest=NombreDeLaClaseTest`
   - Si los tests de contexto Spring fallan por variables de entorno faltantes (`JWT_SECRET`, `WEATHERAPI_API_KEY`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`), exportá valores dummy válidos (con `JWT_SECRET` de al menos 32 bytes, igual que en `.github/workflows/ci.yml`).
   - Los tests `@DataMongoTest` requieren MongoDB corriendo en `localhost:27017` (`docker compose up -d mongodb`) para validar índices únicos y colisiones reales de `@Version`.

3. **Formato Spotless:**
   ```powershell
   .\mvnw.cmd spotless:check
   ```
   - Verifica `removeUnusedImports`, `trimTrailingWhitespace` y `endWithNewline` (`backend/pom.xml`).
   - Si reporta violaciones en archivos que vos modificaste, corregilos manualmente o con `.\mvnw.cmd spotless:apply` (revisando `git diff` para no introducir cambios no relacionados en archivos ajenos a tu tarea).

### 2. Frontend (`frontend/`)
Ejecutar **desde el directorio `frontend/`**:

1. **Lint (ESLint):**
   ```powershell
   npm run lint
   ```
2. **Type-check y Build (`tsc -b && vite build`):**
   ```powershell
   npm run build
   ```

## Formato de Reporte
Al finalizar, reportá los resultados verificados con este resumen:
- **Backend:** Compilación [OK/Fallida], Tests [resultado observado], Spotless [OK / archivos con violaciones].
- **Frontend:** ESLint [OK/Fallido], Build (`tsc -b && vite build`) [OK/Fallido].
- **Acción tomada:** (Si algo falló, qué se corrigió o si quedó alguna violación preexistente fuera del alcance).

---
name: implementation-plan
description: >-
  Creates a technical and architectural implementation plan before writing code when
  the user requests a large feature, complex integration, domain/API redesign, or
  multi-layer change. Do not trigger for small bugfixes, localized UI adjustments, or
  when the user asks to implement directly.
---

# Skill: Planificación Técnica de Implementación

## Propósito
Evitar cambios precipitados en funcionalidades complejas: alinear diseño de dominio, contratos API/UI, concurrencia, resiliencia, observabilidad e infraestructura antes de escribir código.

## Cuándo activar esta Skill
- Al iniciar una **nueva funcionalidad grande**, una **integración entre componentes** (ej. Backend + Frontend + Telegram Bot + Compose/Observabilidad), o un **refactor arquitectónico**.
- **No activar** para bugfixes puntuales, ajustes acotados de UI, correcciones de tests o cuando el usuario pida explícitamente implementar directo.

## Instrucciones paso a paso
1. **No edites código fuente todavía:** Durante esta etapa tu salida debe ser únicamente el plan técnico.
2. **Inspeccionar el contexto real del proyecto:**
   - Revisá los requisitos aplicables (y `correcciones/contexto/` o `docs/ADRs.md` si corresponde).
   - Ubicá qué capas del sistema participan:
     - **Backend (`backend/`)**: monolito modular (`controllers`, `services`, `repositories`, `domain`, `persistence`, `scheduling`, `observabilidad`, handlers de Telegram).
     - **Frontend (`frontend/`)**: SPA React + TypeScript (`src/features/<feature>/`, `src/lib/api.ts`, `src/telemetry.ts`).
     - **Infraestructura / Servicios**: `docker-compose.yaml`, `legacy-clima/`, `observability/`.
3. **Evaluar impactos críticos:**
   - **Dominio y estado:** ¿Qué reglas e invariantes pertenecen a `domain/` vs `services/`? ¿Afecta la máquina de estados (`PROPUESTA`, `CONFIRMADA`, `REPROGRAMADA`, `CANCELADA`, `FINALIZADA`)?
   - **Concurrencia y persistencia:** ¿Requiere `@Version` + `OptimisticLockingRetry`, índices MongoDB u orden seguro de escrituras antes de notificar?
   - **Zona horaria y resiliencia:** ¿Usa `LocalDateTime` con `America/Argentina/Buenos_Aires`? ¿Interactúa con `WeatherAPI`, `LegacyProveedorClima` o Telegram Bot API?
   - **Observabilidad y ADRs:** ¿Impacta en spans/métricas OTel (`ObservabilidadBusquedas`, `telemetry.ts`) o amerita registrar una nueva decisión en `docs/ADRs.md`?
4. **Presentar opciones y resolver dudas:** Si hay más de un enfoque válido, compará brevemente pros/contras y señalá las definiciones abiertas antes de empezar a codificar.

## Formato de Reporte
Respondé con esta estructura clara y concisa:
- **Evaluación del alcance:** Componentes y capas impactadas.
- **Opciones de diseño (si hay alternativas):** Pros y contras de cada enfoque.
- **Riesgos y puntos a validar:** Concurrencia, contratos HTTP (`ProblemDetail`), frontend, entorno/variables, ADRs.
- **Plan propuesto:** Pasos ordenados y recomendación concreta esperando confirmación para implementar.

# Prompts utilizados — Authentication & Profile Service

Este documento recoge los **prompts más importantes** usados con IA generativa (Claude) para construir este microservicio, organizados por fase de desarrollo. No es un registro exhaustivo de cada mensaje: es una selección curada de los prompts que definieron el rumbo de cada entrega, junto con la técnica de ingeniería de prompts aplicada y por qué funcionó.

> Todo el código generado se revisó, se probó y se ajustó manualmente antes de integrarse — la IA aceleró la implementación, pero las decisiones de arquitectura, seguridad y alcance las tomó el equipo.

---

## Índice por fase

1. [Fase 0 — Cobertura de pruebas al 100%](#fase-0--cobertura-de-pruebas-al-100)
2. [Fase 1 — Revisión general y hoja de ruta](#fase-1--revisión-general-y-hoja-de-ruta)
3. [Fase A — Seguridad, roles y panel de administración](#fase-a--seguridad-roles-y-panel-de-administración)
4. [Fase B — Eventos con Kafka y verificación de documentos](#fase-b--eventos-con-kafka-y-verificación-de-documentos)
5. [Fase C — Reputación, reseñas y reportes](#fase-c--reputación-reseñas-y-reportes)
6. [Fase D — Login con GitHub, pruebas de carga y documentación](#fase-d--login-con-github-pruebas-de-carga-y-documentación)
7. [Patrones que se repitieron en todas las fases](#patrones-que-se-repitieron-en-todas-las-fases)

---

## Fase 0 — Cobertura de pruebas al 100%

**Objetivo:** subir la cobertura de JaCoCo de `application.services` al 100%, a partir de un reporte de cobertura ya generado (capturas de pantalla de JaCoCo por clase).

**Prompt principal:**
> **Rol:** Actúa como un experto en testing en Java y Spring Boot.
> **Tarea:** Necesito alcanzar el 100% de cobertura en JaCoCo para el paquete `application.services`.
> **Contexto:** Te comparto el repositorio [link a GitHub] y las capturas del reporte actual de JaCoCo indicando las clases con cobertura faltante.
> **Marco y Restricciones:** Aplica estrictamente el patrón AAA (Arrange, Act, Assert). Incluye casos normales, casos de borde, valores nulos y manejo de excepciones. No modifiques la lógica de negocio.
> **Formato:** Entrégame únicamente el código completo de las clases de test `.java` que necesitan ser actualizadas, listas para copiar y pegar, sin omitir dependencias ni imports.

**Prompts de seguimiento** (iteración corta guiada por Feedback Loop):
> **Feedback:** La clase `UserServiceTest` quedó al 90%. Adjunto la nueva captura de JaCoCo. Faltan por cubrir las líneas marcadas en rojo (manejo de la excepción `UserNotFoundException`).
> **Tarea:** Genera exclusivamente los métodos de prueba faltantes para cubrir esas líneas, aplicando el mismo patrón AAA y restricciones.

**Técnica:** *Zero-shot con contexto visual acumulativo y parámetros estrictos* — sin ejemplos de código de prueba, pero con capturas de pantalla del reporte de cobertura como contexto. Se delimitó exactamente el output esperado y se forzó el uso de buenas prácticas de QA (AAA).

**Por qué funcionó:**
- **Formato de salida explícito:** Pedir "únicamente el código completo" evitó fragmentos de diff ambiguos.
- **Iteración incremental y Feedback Loop:** En vez de pedir el 100% de una sola vez, cada vuelta cerró las ramas restantes (validación crítica con JaCoCo real).
- **Restricción implícita de alcance:** Al pedir clases de test específicas se evitó el "vibe coding sin control", manteniendo el cambio acotado y modular.

---

## Fase 1 — Revisión general y hoja de ruta

**Objetivo:** con la cobertura ya al 100%, decidir qué construir después y en qué orden.

**Prompt principal:**
> **Rol:** Actúa como un Arquitecto de Software Senior.
> **Tarea:** Diseña el plan de ejecución y priorización para las tareas restantes de nuestro microservicio.
> **Contexto:** Ya alcanzamos el 100% de cobertura de pruebas unitarias. Las tareas pendientes son: refactorización general, envío de documentos para verificación (HITL), integración asíncrona (Kafka/Event Hubs), login con GitHub, sistema de calificaciones/reputación, pruebas de carga (k6) y documentación final.
> **Marco (Chain-of-Thought):** Analiza paso a paso las dependencias técnicas entre estas tareas. Define el orden óptimo de implementación argumentando los trade-offs de arquitectura (ej. por qué hacer seguridad antes que mensajería).
> **Formato:** Entrégame una hoja de ruta numerada con la justificación arquitectónica de cada paso.

**Técnica:** *Chain-of-Thought delegado y Persona Prompting* — el usuario no pidió ejecutar código, sino *planificar*. Se forzó a la IA a razonar sobre dependencias como arquitecto, antes de escribir una sola línea de código.

**Por qué funcionó:**
- **Autonomía delegada con criterio propio del usuario:** Condicionar a la IA a justificar su razonamiento permitió validar la ruta de desarrollo antes de implementarla.
- **Alcance amplio, pero verificable en pasos:** Cada punto de la lista se convirtió después en una fase independiente, aplicando el pilar de Vibe Coding: construir de manera modular y por fases aisladas.

---

## Fase A — Seguridad, roles y panel de administración

**Objetivo:** cerrar los huecos de seguridad encontrados en la revisión y construir el panel de administración.

**Prompt principal (arranque de la fase):**
> **Rol:** Actúa como un Desarrollador Backend Senior experto en Clean Architecture y Spring Security.
> **Tarea:** Implementa la validación de roles, seguridad de endpoints y la lógica de administración.
> **Contexto:** Adjunto el backlog de Jira y el documento de requerimientos (Historias RF-AUTH-1 a RF-AUTH-14). La comunicación asíncrona usará Azure Event Hubs, pero el microservicio consumidor aún no existe.
> **Restricciones (Vibe Coding):**
> - Modularidad: Modifica únicamente los adaptadores web y servicios de dominio de Auth.
> - Protege el registro: Bloquea explícitamente la creación de usuarios con rol `ADMIN` desde los endpoints públicos.
> - Deja los puertos/interfaces listos para el patrón Outbox, sin implementar la integración real aún.
> **Formato:** Entrégame los cambios separados por archivo.

**Técnica:** *Ingeniería de contexto (Backlog) + Restricciones claras.* El prompt incluyó contexto de negocio real (Jira) y aplicó "restricciones negativas" (lo que NO debe hacer) para mantener el control.

**Por qué funcionó:**
- **Contexto de negocio real:** Evitó que la IA inventara reglas de negocio; cada decisión de esta fase se pudo trazar a un requerimiento del backlog.
- **Restricción explícita de arquitectura futura:** Condicionó decisiones concretas aislando el problema de mensajería (que aún no existía) mediante puertos, evitando que la IA alucinara integraciones complejas e innecesarias en ese momento.

---

## Fase B — Eventos con Kafka y verificación de documentos

**Objetivo:** implementar la mensajería asíncrona (patrón outbox) y el flujo de subida/verificación de documentos de identidad sobre Azure Blob Storage.

**Prompt principal:**
> **Rol:** Actúa como Arquitecto Cloud experto en Azure y Seguridad.
> **Contexto:** Vamos a implementar el flujo de documentos en Azure Blob Storage. Adjunto mi archivo `.env` (las credenciales críticas ya fueron rotadas).
> **Tarea (Self-Critique / Check-and-Balance):** Evalúa estas dos opciones para el acceso del sistema HITL (Human-in-the-Loop) a los documentos:
> - Opción A: HITL accede usando su propia credencial de solo lectura al contenedor.
> - Opción B: Este microservicio genera y entrega un enlace temporal (SAS URL) a HITL.
> **Marco (Chain-of-Thought):** Piensa paso a paso y compara explícitamente los trade-offs de seguridad, mantenibilidad y acoplamiento de ambas opciones.
> **Formato:** Dame tu recomendación final justificada. No generes código hasta que yo valide la decisión.

**Técnica:** *Self-Critique y Tree of Thoughts acotado.* En lugar de pedir código directamente, se redujo el problema a dos opciones de diseño y se obligó a la IA a evaluar los trade-offs.

**Por qué funcionó:**
- **Reducir el espacio de diseño** forzó una respuesta técnica precisa en vez de código rápido y mal sustentado.
- El **.env real** compartido como contexto permitió detectar errores concretos de configuración (como una sintaxis incorrecta) que una simple descripción no habría revelado.
- Se respetó el rol del desarrollador como **curador/director**, validando el enfoque antes de proceder con el código.

---

## Fase C — Reputación, reseñas y reportes

**Objetivo:** implementar calificaciones entre usuarios, recálculo de reputación y el sistema de reportes con suspensión automática por umbral.

**Prompt principal:**
> **Tarea:** Implementa el módulo de Reputación, Reseñas y Reportes para cerrar esta fase.
> **Contexto:** Basado en la memoria de nuestro backlog (Revisa requerimientos F4 'Reputación' y F5 'Moderación' que compartí anteriormente). Aplica la regla de suspensión automática de cuentas al superar el umbral de reportes.
> **Restricciones:**
> - Las reseñas y reportes deben ser entidades propias en el dominio, no simples atributos en la clase `Profile`.
> - IMPORTANTE: No inventes integraciones con el Task Service (aún no existe). Maneja la validación de manera local por ahora.
> **Formato:** Implementa usando iteración rápida: entrégame primero el código de las Entidades y Puertos del Dominio. Espera mi validación (feedback loop) antes de codificar los Casos de Uso.

**Técnica:** *Contexto acumulativo + Iteración controlada.* La IA se basó en el contexto heredado del chat, aplicando instrucciones estrictas de Vibe Coding (paso a paso, sin intentar hacer todo el flujo en un solo prompt).

**Por qué funcionó:**
- Evitó reexplicar reglas de negocio ya establecidas en fases anteriores.
- Permitió a la IA **señalar explícitamente lo que no podía resolver todavía**, aplicando el principio de Vibe Coding de no intentar adivinar sistemas externos (Task Service).

---

## Fase D — Login con GitHub, pruebas de carga y documentación

**Objetivo:** cerrar el roadmap con login social por GitHub, script de carga (k6) y la documentación completa (README).

**Prompt principal (Pruebas - Few-shot):**
> **Rol:** Actúa como un Ingeniero QA experto en Performance.
> **Tarea:** Crea un script de pruebas de carga usando k6 para este microservicio.
> **Contexto:** El flujo de negocio real a probar es: Register -> Verify-OTP (vía Redis) -> Login -> Fetch Profile.
> **Ejemplo (Few-Shot):** Te comparto un script de k6 que hice para otro proyecto: [Script de k6 completo].
> **Restricciones:** Adapta el script a nuestra lógica de negocio, pero respeta estrictamente el estilo del ejemplo:
> - Define métricas personalizadas (Counters, Rate, Trend).
> - Genera un reporte ASCII en la función `handleSummary`.
> - Incluye la lógica de reintentos con backoff exponencial.

**Prompt del README (Structured Output):**
> **Rol:** Actúa como un Technical Writer Senior.
> **Tarea:** Redacta el `README.md` final del microservicio.
> **Contexto:** Adjunto 5 imágenes con diagramas C4 (creados en draw.io) que detallan la arquitectura actual.
> **Formato (Structured Output):** Sigue ESTRICTAMENTE esta estructura y usa estos emojis:
> # 🛡️ Titulo microservicio
> ## 👥 Desarrolladores [Nombres]
> ## 📖 Contenido
> ## 🏗️ Arquitectura
> ## 📂 Clean - Hexagonal Structure
> ## 🔌 Api Endpoints
> ## 🛠 Integración con Microservicios
> ## 🧰 Technologies
> ## 📊 Diagramas (Explica cada diagrama basándote estrictamente en lo que se ve en la imagen).
> **Restricciones:** Señala explícitamente dónde el código actual ha evolucionado y difiere del diagrama (ej. entidades de reseñas separadas). No inventes características que no estén en el repositorio.

**Técnica:** ***Few-shot prompting*** explícito para k6 (usar un código base como plantilla de estilo) y ***Structured Output*** para el README, forzando restricciones de formato y contexto visual (imágenes de arquitectura).

**Por qué funcionó:**
- El **ejemplo de k6 fijó el estilo exacto**, garantizando que el output tuviera el estándar de calidad esperado sin necesidad de describir la lógica de métricas desde cero.
- La **estructura estricta del README** eliminó las ambigüedades. La IA se limitó a sintetizar conocimiento técnico verídico en los bloques requeridos.
- Las imágenes funcionaron como **fuente de verdad visual**; la IA respetó el diseño dibujado en vez de alucinar una arquitectura distinta.

---

## Patrones que se repitieron en todas las fases

| Principio (según la guía SDLC / Vibe Coding) | Cómo se aplicó en este proyecto |
|---|---|
| **Contexto Relevante** | Cada fase se acompañó del repositorio real, el `.env`, reportes de JaCoCo o el backlog. Nunca se le pidió a la IA "adivinar" el estado del código. |
| **Iterar (Feedback Loop)** | Ninguna fase se cerró en un solo mensaje. Se ejecutó `mvn clean verify`, se validaron pruebas unitarias, y se devolvió el error exacto (o el Diff) para correcciones puntuales. |
| **Revisar (Curación humana)** | El desarrollador actuó como **Director**. Cada bloque entregado se revisó conceptual y técnicamente antes de hacer commit. Nunca se asumió que la IA era infalible. |
| **Proteger Datos** | Aunque se pasó un `.env` para tener contexto de infraestructura, se garantizó que eran credenciales de prueba o rotadas, mitigando el riesgo de fuga. |
| **Modularidad / Cambios acotados** | Se forzó a la IA a pensar en "arquitectura limpia": interfaces y puertos aislados, cambios limitados a archivos específicos, evitando modificaciones masivas incontrolables. |
| **Restricciones Claras** | Instrucciones negativas ("No inventes integraciones", "No modifiques la lógica", "No generes código hasta que apruebe") previnieron que la IA perdiera el "vibe" y rompiera funcionalidades existentes. |
# Eventos (RabbitMQ) y verificación de documentos

Este documento es el **contrato de integración** de Authentication & Profile (`auth-profile-service`) con los demás
microservicios, en especial **Security and Audit HITL** (RPAD) y **Task Service**.

## 1. Cómo viajan los mensajes

- Broker: **RabbitMQ** administrado en **CloudAMQP** (AMQP 0-9-1 sobre TLS, `amqps`, puerto 5671). Todos los
  microservicios usan la misma instancia y el mismo *virtual host*.
- Hay **un exchange de tipo `topic`**, durable, llamado `taskit.events`. La **routing key** de cada mensaje es el
  nombre del canal (las de la sección 2, las mismas que antes eran los topics de Kafka). El tipo del evento va
  dentro del mensaje (`eventType`).
- Cada microservicio que consume declara **su propia cola durable** y la liga al exchange con la routing key que le
  interesa. **RabbitMQ no guarda mensajes que no llegan a ninguna cola** (a diferencia de Kafka): la cola del
  consumidor debe existir y estar ligada *antes* de que se publique el evento, o ese evento se pierde para él.
- Entrega **al menos una vez**: un mismo evento puede llegar repetido. Todo consumidor debe deduplicar por `eventId`.
- RabbitMQ no tiene claves de partición. El `userId` (o el correo en auditoría) viaja en la cabecera `key` del mensaje
  y el orden se conserva por cola (un consumidor por cola, sin concurrencia).
- Este MS no envía directo al broker: guarda el evento en Mongo (colección `outbox_events`) y un proceso lo envía cada
  2 s con confirmación del broker (*publisher confirm*) y solo entonces lo borra. Si RabbitMQ cae, los eventos se
  acumulan y salen al recuperarse; no se pierden. Los mensajes se publican como JSON (`application/json`) y
  persistentes.

### Sobre común (todos los mensajes)

```json
{
  "eventId": "6f1c1c7e-3c3a-4d0e-9b57-2f0f0d6c9a10",
  "eventType": "IdentityDocumentSubmitted",
  "version": 1,
  "occurredAt": "2026-09-21T15:04:05.123Z",
  "producer": "auth-profile-service",
  "payload": { }
}
```

Los consumidores deben **ignorar los campos y los `eventType` que no conozcan** (compatibilidad hacia adelante).

## 2. Topics

| Routing key | Quién publica | Quién consume | Contenido |
|---|---|---|---|
| `taskit.auth.events` | Auth & Profile | HITL, Task Service, otros | Eventos de negocio de usuarios y perfiles |
| `taskit.auth.audit` | Auth & Profile | Security and Audit | Trazas de seguridad (logins, accesos a documentos) |
| `taskit.security.events` | Security and Audit HITL | **Auth & Profile** | Resultado de revisiones y sanciones |
| `taskit.auth.dead-letter` | RabbitMQ (dead letter exchange `taskit.dlx`) | Operación | Mensajes que no se pudieron procesar; es una **cola**, no se publica a mano |
| `taskit.task.events` | Task Service | **Auth & Profile** | `TaskCompleted` (reputación) |

## 3. Eventos que publica Auth & Profile

### `taskit.auth.events` (cabecera `key` = `userId`)

**UserRegistered**: se creó una cuenta (registro local o primer login con Google).
```json
{ "userId": "…", "email": "…", "name": "…", "role": "SEEKER|PROVIDER", "authProvider": "LOCAL|GOOGLE", "registeredAt": "…" }
```

**IdentityDocumentSubmitted**: el usuario subió su documento. **Es el evento que dispara la revisión HITL.**
```json
{ "userId": "…", "email": "…", "fullName": "…", "documentType": "CEDULA|PASSPORT|OTHER",
  "contentType": "application/pdf|image/jpeg|image/png", "sizeBytes": 123456,
  "blobName": "<userId>/<uuid>.pdf", "submittedAt": "…" }
```
No incluye el archivo ni una URL. Para verlo, ver la sección 5.

**IdentityVerificationUpdated**: cambió el estado de verificación de identidad (lo consulta, por ejemplo, Task Service).
```json
{ "userId": "…", "status": "VERIFIED|REJECTED", "reason": "…|null", "reviewedBy": "…", "updatedAt": "…" }
```

**AccountStatusChanged**: una cuenta se suspendió, bloqueó o reactivó (por un admin o por una sanción de Security).
```json
{ "userId": "…", "email": "…", "previousStatus": "ACTIVE", "newStatus": "SUSPENDED|BLOCKED|ACTIVE|PENDING_VERIFICATION",
  "reason": "…|null", "changedBy": "admin@… | security-service:<reportId>", "changedAt": "…" }
```

**AccountDeleted**: el usuario (o un admin) eliminó la cuenta. Los demás servicios deben limpiar sus datos.
```json
{ "userId": "…", "email": "…", "deletedAt": "…" }
```

**ReviewCreated**: se registró una calificación (ver sección 6).
```json
{ "reviewId": "…", "taskId": "…", "reviewerId": "…", "revieweeId": "…", "rating": 1-5, "createdAt": "…" }
```

**ReputationUpdated**: cambió el promedio de reputación de un usuario (tras cada `ReviewCreated`).
```json
{ "userId": "…", "reputationScore": 4.33, "totalReviews": 12, "updatedAt": "…" }
```

**UserReported**: un usuario reportó a otro (ver sección 6). Es el evento que Security and Audit debe consumir
para su cola de moderación; este MS resuelve el reporte solo con un endpoint manual mientras HITL no exista.
```json
{ "reportId": "…", "reporterId": "…", "revieweeId": "…", "reason": "…", "comment": "…|null", "createdAt": "…" }
```

### `taskit.auth.audit` (cabecera `key` = correo del usuario)

**SecurityAudit**
```json
{ "action": "LOGIN_SUCCESS|LOGIN_FAILED|LOGIN_BLOCKED|IDENTITY_DOCUMENT_ACCESSED",
  "subject": "correo o userId afectado", "actor": "quién ejecutó la acción (null si es el propio usuario)",
  "ipAddress": "…|null", "occurredAt": "…" }
```

## 4. Eventos que consume Auth & Profile (routing key `taskit.security.events`)

Los publica Security and Audit HITL en el exchange `taskit.events` con el mismo sobre (`producer` libre, p. ej. `security-service`). Este MS los recibe en su cola `auth-profile.security-events`.

**IdentityVerificationResolved**: resultado de la revisión humana del documento.
```json
{ "userId": "…", "decision": "APPROVED|REJECTED", "reason": "obligatorio si REJECTED", "reviewedBy": "id del revisor" }
```
Efecto: el perfil pasa a `VERIFIED` o `REJECTED` (con el motivo) y se publica `IdentityVerificationUpdated`.
Es idempotente. Si el perfil no está en `PENDING_REVIEW` (o el usuario ya no existe) el mensaje va a `taskit.auth.dead-letter`.

**AccountSanctionApplied**: sanción por reportes o por moderación.
```json
{ "userId": "…", "action": "SUSPEND|BLOCK|REINSTATE", "reason": "obligatorio en SUSPEND y BLOCK", "reportId": "…" }
```
Efecto: cambia el estado de la cuenta, elimina su refresh token y rechaza sus access tokens vigentes. Se publica
`AccountStatusChanged`. No se puede sancionar a cuentas ADMIN.

Errores de datos (JSON inválido, acción o decisión desconocida, usuario inexistente) no se reintentan y van al dead letter.
Los errores transitorios se reintentan 3 veces (1 s entre intentos) antes de ir al dead letter. Se rechazan sin reencolar y RabbitMQ los mueve a la cola `taskit.auth.dead-letter` (con la cabecera `x-death` que indica la cola de origen y el motivo).

## 5. Flujo de verificación de documentos

```
Front ── POST /api/v1/profiles/{userId}/document (multipart: file, documentType) ──► Auth & Profile
   1. Valida: no vacío, ≤ 5 MB, PDF/JPG/PNG por firma real del archivo (no por extensión ni Content-Type)
   2. Guarda en Amazon S3, bucket PRIVADO "taskit-identity-documents", nombre {userId}/{uuid}.{ext}
   3. Perfil → PENDING_REVIEW y publica IdentityDocumentSubmitted (taskit.auth.events)

HITL ◄── IdentityDocumentSubmitted (routing key taskit.auth.events)
   4. El revisor (usuario con rol ADMIN) pide el archivo:
      GET /api/v1/admin/verification-documents/{userId}/access   (Authorization: Bearer <JWT de un ADMIN>)
      → { "url": "https://…/taskit-identity-documents/…?X-Amz-Signature=…", "expiresAt": "…", "documentType": "…", … }
      El enlace es de SOLO LECTURA y vence en 10 minutos (SAS_TTL_MINUTES). Cada acceso queda auditado
      (log + evento SecurityAudit IDENTITY_DOCUMENT_ACCESSED).
   5. El revisor decide y HITL publica IdentityVerificationResolved (routing key taskit.security.events)

Auth & Profile ◄── IdentityVerificationResolved
   6. Perfil → VERIFIED o REJECTED y publica IdentityVerificationUpdated
   7. Si fue REJECTED, el usuario puede subir otro documento (el anterior se borra del bucket)
```

- El bucket de documentos **no tiene acceso público**; ningún servicio recibe credenciales de S3: el servicio usa el rol IAM de su tarea en ECS.
  S3 cifra en reposo por defecto y todo el tráfico va por HTTPS.
- Mientras HITL no exista, un ADMIN puede resolver a mano con
  `POST /api/v1/admin/verification-documents/{userId}/resolution` y cuerpo `{ "decision": "APPROVED|REJECTED", "reason": "…" }`
  (usa la misma lógica que el evento).
- Un usuario con documento en `PENDING_REVIEW` no puede subir otro; uno `VERIFIED` tampoco.

## 6. Reputación, reseñas y reportes

- `POST /api/v1/reviews` (autenticado): registra la calificación (1-5) que el usuario deja a otro por una tarea
  (`taskId`, `revieweeId`, `rating`, `comment` opcional). Un mismo usuario solo puede calificar una misma tarea una
  vez (409 si repite). Tras guardar, recalcula la reputación completa del perfil calificado (promedio de todas sus
  reseñas, nunca editable a mano) y publica `ReviewCreated` y `ReputationUpdated`.
  **Pendiente:** no valida todavía contra Task Service que la tarea exista, esté completada o que el reviewer haya
  participado en ella; eso requiere que ese microservicio exista y defina cómo consultarlo o qué evento publica al
  completar una tarea.
- `GET /api/v1/reviews/user/{userId}` (autenticado): reseñas recibidas por un usuario, paginado, más recientes primero.
- `POST /api/v1/profiles/{userId}/reports` (autenticado): reporta un perfil por comportamiento inapropiado o fraude
  (`reason`, `comment` opcional). Un mismo usuario no puede tener dos reportes abiertos contra el mismo perfil.
  Si el perfil acumula `REPORTS_AUTO_SUSPEND_THRESHOLD` reportes abiertos (por defecto 3), se suspende preventivamente
  y se dispara `AccountStatusChanged` con `changedBy = "system:auto-report-threshold"`.
- `GET /api/v1/admin/reports?status=&page=&size=` y `PATCH /api/v1/admin/reports/{reportId}/status` (solo ADMIN):
  listar y resolver reportes a mano (`RESOLVED` o `DISMISSED`) mientras Security and Audit HITL no los consuma
  directamente del topic y aplique sus propias decisiones vía `AccountSanctionApplied`.

## 7. Configuración de RabbitMQ (CloudAMQP)

Todos los microservicios se conectan a la misma instancia de CloudAMQP (AWS `sa-east-1`). La URL completa con el
usuario y la contraseña sale del panel de la instancia (sección *AMQP details*, campo **URL**) y es un **secreto**:
va solo en la variable de entorno `RABBITMQ_URL` (en el `.env` local o en las variables del servicio de ECS), nunca en
el código, en un `.env.example`, en un commit ni en un chat. Si se expone, se rota desde el botón *Rotate password* del
panel y se actualiza en todos los servicios.

**Topología** (la declara este MS al arrancar; los nombres se pueden cambiar con las variables `RABBITMQ_*`):

| Elemento | Nombre por defecto | Detalle |
|---|---|---|
| Exchange de eventos | `taskit.events` | `topic`, durable. **Todos los equipos deben declararlo igual**: si uno lo declara con otro tipo o durabilidad, RabbitMQ rechaza la conexión con `PRECONDITION_FAILED` |
| Dead letter exchange | `taskit.dlx` | `direct`, durable |
| Cola de dead letter | `taskit.auth.dead-letter` | Durable, ligada a `taskit.dlx` con la misma routing key |
| Cola de seguridad (consume este MS) | `auth-profile.security-events` | Durable, ligada a `taskit.events` con `taskit.security.events`; falla hacia `taskit.dlx` |
| Cola de tareas (consume este MS) | `auth-profile.task-events` | Durable, ligada a `taskit.events` con `taskit.task.events`; falla hacia `taskit.dlx` |

Este MS **solo publica** (no declara colas para) `taskit.auth.events` y `taskit.auth.audit`: las colas de esos canales
las declara cada consumidor (HITL, Task Service, Payments...) ligadas al mismo exchange.

**Variables de entorno de este MS** (servicio de ECS):

```
RABBITMQ_URL=amqps://<usuario>:<contraseña>@<host>.lmq.cloudamqp.com/<vhost>
RABBITMQ_ENABLED=true
```

El resto (`RABBITMQ_EXCHANGE`, `RABBITMQ_DEAD_LETTER_EXCHANGE`, `RABBITMQ_SECURITY_QUEUE`, `RABBITMQ_SECURITY_ROUTING_KEY`,
`RABBITMQ_TASK_QUEUE`, `RABBITMQ_TASK_ROUTING_KEY`) es opcional y solo se usa si el equipo acuerda otros nombres.

Si RabbitMQ no está disponible el servicio arranca igual: los eventos quedan en el outbox y se envían después.
`RABBITMQ_ENABLED=false` apaga por completo el envío y el consumo (útil para pruebas sin broker).

Con `amqps` el tráfico va cifrado (TLS, puerto 5671); con `amqp` (puerto 5672) no, así que en la nube se usa siempre `amqps`.

## 8. Desarrollo local

```
docker compose up -d        # RabbitMQ (localhost:5672, panel en http://localhost:15672, guest/guest) y Redis (localhost:6379)
```
En el `.env`: `REDIS_HOST=localhost`, `REDIS_PORT=6379`, `REDIS_SSL=false`. RabbitMQ ya apunta a `amqp://guest:guest@localhost:5672/`
por defecto. Para probar el consumo, en el panel de RabbitMQ abre el exchange `taskit.events`, sección *Publish message*,
usa la routing key `taskit.security.events` y como cuerpo un sobre como los de la sección 4.

## 9. Guía para el equipo de Security and Audit HITL

- **Consumir**: declarar una cola durable propia, ligarla al exchange `taskit.events` con las routing keys
  `taskit.auth.events` (filtrar `eventType = IdentityDocumentSubmitted`) y `taskit.auth.audit`, y consumir de ella.
  Deduplicar por `eventId`. Declarar la cola **antes** de que se publiquen los eventos que les interesan.
- **Publicar** en el exchange `taskit.events`, con la routing key `taskit.security.events`, los eventos de la sección 4
  usando el sobre común (generar un `eventId` UUID nuevo por evento; reenviar el mismo `eventId` es seguro, se ignora).
  Conviene publicar como JSON persistente y con *publisher confirms* activados.
- El revisor se autentica con el JWT que emite Auth & Profile (`POST /api/v1/auth/login`) y debe tener rol `ADMIN`.

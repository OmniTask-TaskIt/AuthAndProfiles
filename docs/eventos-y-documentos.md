# Eventos (Kafka) y verificación de documentos

Este documento es el **contrato de integración** de Authentication & Profile (`auth-profile-service`) con los demás
microservicios, en especial **Security and Audit HITL** (RPAD) y **Task Service**.

## 1. Cómo viajan los mensajes

- Broker: **Apache Kafka 3.8** (modo KRaft) en una EC2 de AWS, con SASL/PLAIN dentro de la VPC.
- Se usan pocos topics "gruesos" y el tipo del evento va dentro del mensaje.
- Entrega **al menos una vez**: un mismo evento puede llegar repetido. Todo consumidor debe deduplicar por `eventId`.
- La clave de partición es siempre el `userId` (o el correo en auditoría) para conservar el orden por usuario.
- Este MS no envía directo a Kafka: guarda el evento en Mongo (colección `outbox_events`) y un proceso lo envía cada
  2 s y lo borra. Si Kafka cae, los eventos se acumulan y salen al recuperarse; no se pierden.

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

| Topic (Event Hub) | Quién publica | Quién consume | Contenido |
|---|---|---|---|
| `taskit.auth.events` | Auth & Profile | HITL, Task Service, otros | Eventos de negocio de usuarios y perfiles |
| `taskit.auth.audit` | Auth & Profile | Security and Audit | Trazas de seguridad (logins, accesos a documentos) |
| `taskit.security.events` | Security and Audit HITL | **Auth & Profile** | Resultado de revisiones y sanciones |
| `taskit.auth.dead-letter` | Auth & Profile | Operación | Mensajes que no se pudieron procesar |
| `taskit.task.events` | Task Service | **Auth & Profile** | `TaskCompleted` (reputación) |

## 3. Eventos que publica Auth & Profile

### `taskit.auth.events` (clave = `userId`)

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

### `taskit.auth.audit` (clave = correo del usuario)

**SecurityAudit**
```json
{ "action": "LOGIN_SUCCESS|LOGIN_FAILED|LOGIN_BLOCKED|IDENTITY_DOCUMENT_ACCESSED",
  "subject": "correo o userId afectado", "actor": "quién ejecutó la acción (null si es el propio usuario)",
  "ipAddress": "…|null", "occurredAt": "…" }
```

## 4. Eventos que consume Auth & Profile (`taskit.security.events`)

Los publica Security and Audit HITL con el mismo sobre (`producer` libre, p. ej. `security-service`).

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
Los errores transitorios se reintentan 3 veces (1 s entre intentos) antes de ir al dead letter.

## 5. Flujo de verificación de documentos

```
Front ── POST /api/v1/profiles/{userId}/document (multipart: file, documentType) ──► Auth & Profile
   1. Valida: no vacío, ≤ 5 MB, PDF/JPG/PNG por firma real del archivo (no por extensión ni Content-Type)
   2. Guarda en Azure Blob, contenedor PRIVADO "identity-documents", nombre {userId}/{uuid}.{ext}
   3. Perfil → PENDING_REVIEW y publica IdentityDocumentSubmitted (taskit.auth.events)

HITL ◄── IdentityDocumentSubmitted
   4. El revisor (usuario con rol ADMIN) pide el archivo:
      GET /api/v1/admin/verification-documents/{userId}/access   (Authorization: Bearer <JWT de un ADMIN>)
      → { "url": "https://…/identity-documents/…?sv=…&sig=…", "expiresAt": "…", "documentType": "…", … }
      El enlace es de SOLO LECTURA y vence en 10 minutos (SAS_TTL_MINUTES). Cada acceso queda auditado
      (log + evento SecurityAudit IDENTITY_DOCUMENT_ACCESSED).
   5. El revisor decide y HITL publica IdentityVerificationResolved (taskit.security.events)

Auth & Profile ◄── IdentityVerificationResolved
   6. Perfil → VERIFIED o REJECTED y publica IdentityVerificationUpdated
   7. Si fue REJECTED, el usuario puede subir otro documento (el anterior se borra del blob)
```

- El contenedor de documentos **no tiene acceso público**; ningún servicio recibe credenciales de Azure Storage.
  Azure Storage cifra en reposo por defecto y todo el tráfico va por HTTPS.
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

## 7. Configuración de Kafka en AWS

Kafka corre en la misma EC2 que Redis, en un contenedor Docker (`apache/kafka:3.8.0`, un solo nodo en modo KRaft).

1. **Listener:** `BROKER://:9092` con `SASL_PLAINTEXT` y mecanismo `PLAIN`. El broker anuncia su **IP privada**
   (`KAFKA_ADVERTISED_LISTENERS=BROKER://<IP privada de la EC2>:9092`), así que solo se llega desde dentro de la VPC.
2. **Red:** el grupo de seguridad de la EC2 permite TCP 9092 solo desde el rango de la VPC. No hay acceso desde internet.
3. **Usuario:** `taskit`, con la contraseña definida en `KAFKA_LISTENER_NAME_BROKER_PLAIN_SASL_JAAS_CONFIG` al crear el contenedor.
4. **Topics** (3 particiones, factor de replicación 1): `taskit.auth.events`, `taskit.auth.audit`,
   `taskit.security.events`, `taskit.task.events` y `taskit.auth.dead-letter`. Todos los servicios usan el mismo
   usuario y los mismos topics (Payments y Security incluidos).
5. Variables de entorno de este MS (servicio de ECS):

```
KAFKA_ENABLED=true
KAFKA_BOOTSTRAP_SERVERS=<IP privada de la EC2>:9092
KAFKA_SECURITY_PROTOCOL=SASL_PLAINTEXT
KAFKA_SASL_MECHANISM=PLAIN
KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.plain.PlainLoginModule required username="taskit" password="<contraseña de Kafka>";
KAFKA_CONSUMER_GROUP=auth-profile-service
```

Si Kafka no está disponible el servicio arranca igual: los eventos quedan en el outbox y se envían después.
`KAFKA_ENABLED=false` apaga por completo el envío y el consumo (útil para pruebas sin broker).

Nota: `SASL_PLAINTEXT` no cifra el tráfico. Es aceptable mientras el broker solo sea accesible dentro de la VPC;
para exponerlo fuera hay que pasar a `SASL_SSL` con certificado.

## 8. Desarrollo local

```
docker compose up -d        # Kafka (localhost:9092) y Redis (localhost:6379)
```
En el `.env`: `REDIS_HOST=localhost`, `REDIS_PORT=6379`, `REDIS_SSL=false`. Kafka ya apunta a localhost por defecto.
Para probar el consumo, publicar a mano en `taskit.security.events` (por ejemplo con `kafka-console-producer.sh`
dentro del contenedor) un sobre como los de la sección 4.

## 9. Guía para el equipo de Security and Audit HITL

- **Consumir** `taskit.auth.events` (filtrar `eventType = IdentityDocumentSubmitted`) y `taskit.auth.audit`.
  Deduplicar por `eventId`.
- **Publicar** en `taskit.security.events` los eventos de la sección 4 usando el sobre común (generar un `eventId` UUID
  nuevo por evento; reenviar el mismo `eventId` es seguro, se ignora).
- El revisor se autentica con el JWT que emite Auth & Profile (`POST /api/v1/auth/login`) y debe tener rol `ADMIN`.

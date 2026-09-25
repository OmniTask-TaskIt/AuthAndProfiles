# Pruebas de carga (k6)

## Requisitos
- [k6](https://k6.io/docs/get-started/installation/) instalado localmente, o Docker.
- El servicio corriendo y accesible (local, `docker compose up -d` + `mvn spring-boot:run`, o el ambiente desplegado).

## Ejecutar
```bash
# Contra localhost:8080 (valor por defecto)
k6 run auth-profile-load-test.js

# Contra otro ambiente
k6 run -e BASE_URL=https://tu-ambiente.azurewebsites.net auth-profile-load-test.js

# Con Docker, sin instalar k6
docker run --rm -i --network=host -e BASE_URL=http://localhost:8080 grafana/k6 run - < auth-profile-load-test.js
```

Al terminar, además del resumen en consola, se genera `load-test-summary.json` en el directorio desde el
que se ejecutó k6.

## Qué mide
El script simula el flujo real de un usuario nuevo contra los endpoints públicos de autenticación:

1. `POST /api/v1/auth/register` — registro con datos únicos por iteración.
2. `POST /api/v1/auth/verify-otp` — el script lee el código real desde Redis (ver abajo), no lo inventa.
3. `POST /api/v1/auth/login` — login con la cuenta recién verificada.
4. `GET /api/v1/profiles/{userId}` — lectura del perfil con el access token, con reintentos.

### Por qué el script necesita acceso a Redis
A diferencia del proyecto de referencia (que no verifica correo), aquí `register` no devuelve tokens: el
OTP se manda por correo real (Resend) y hay que verificarlo antes de poder hacer login. Para poder correr
la prueba de carga sin bandeja de correo, el script lee el código OTP directamente de Redis con la misma
convención de llave que usa el backend (`refresh_token:otp:<email>`: `TokenRedisRepository` antepone
`refresh_token:` a toda llave), usando la extensión `k6/x/redis` (`xk6-redis`).
Esto es exclusivo de este script de carga: nunca se expone un endpoint que devuelva el OTP.

Build del binario con la extensión (una sola vez; si ya existe el archivo `k6` en esta carpeta, se omite):
```bash
docker run --rm -v "${PWD}:/xk6" -w /xk6 grafana/xk6 build --with github.com/grafana/xk6-redis@latest
```
Ejecutar con el binario generado en vez de `k6` (PowerShell; en bash usa `\` en vez de el acento grave):
```powershell
docker run --rm -v "${PWD}:/xk6" -w /xk6 --entrypoint /xk6/k6 `
  -e BASE_URL=http://host.docker.internal:8080 `
  -e REDIS_HOST=host.docker.internal `
  -e REDIS_PORT=6379 `
  grafana/xk6 run auth-profile-load-test.js
```
Variables de entorno: `BASE_URL`, `REDIS_HOST` (default `localhost`), `REDIS_PORT` (default `6379`),
`REDIS_PASSWORD` (vacío por defecto) y `REDIS_TLS=true` para Azure Cache for Redis (puerto 6380).

Para que no se envíen correos reales, el servicio debe arrancar con `EMAIL_ENABLED=false`; el OTP se genera
y se guarda en Redis igual.

## Umbrales (`thresholds`)
Son umbrales **de desarrollo**, por endpoint y con margen sobre lo medido. El objetivo original era
`p(95) < 800 ms`, pero en la prueba local contra MongoDB Atlas remoto no se cumple (ver abajo).

| Umbral | Meta | Por qué |
|---|---|---|
| `register_duration_ms p(95)` | < 5000 ms | Hash BCrypt + tres escrituras en Mongo + Redis |
| `otp_duration_ms p(95)` | < 3000 ms | Lectura de Redis + lectura/escritura del usuario |
| `login_duration_ms p(95)` | < 3500 ms | Hash BCrypt + lectura del usuario + escritura del refresh token |
| `profile_duration_ms p(95)` | < 2000 ms | Lectura simple por `userId` |
| `login_success_rate` | > 0.98 | El login es el flujo más crítico del servicio |
| `register_errors` | count < 5 | Errores de validación no deberían escalar con la carga |
| `profile_errors` | count < 10 | Lectura simple, sin dependencias externas |

## Resultado de referencia
100 VUs máximos, 3 min 30 s, 7 990 peticiones (37.82 req/s), 0 % de fallos y 1 598 flujos completos.
P95 por endpoint: register 4 205 ms, verify-otp 2 149 ms, login 2 762 ms, profile 1 171 ms.

![Resumen](../docs/img/reporte-carga-resumen.png)
![Umbrales](../docs/img/reporte-carga-umbrales.png)

**Estos valores validan la corrección funcional bajo carga, no un SLA de producción.** Un
`GET /profiles/{id}` por un campo ya indexado sigue en ~1.2 s, lo que sugiere que el límite lo pone el
entorno (app local y Atlas remoto), aunque esa causa no se confirmó con las métricas de Atlas. Para validar
el objetivo de 800 ms hay que repetir la prueba con la app y la base de datos en la misma región y un tier
dedicado, y volver a exigir el umbral estricto.

## Problemas conocidos que ya se resolvieron
- La llave del OTP es `refresh_token:otp:<email>`, no `otp:<email>`.
- xk6-redis rechaza `get` con `redis: nil` cuando la llave no existe; hay que capturarlo con `try/catch`.
- El paso de perfil busca por email (camino indexado); la búsqueda por nombre es un regex sin anclar.

## No probado a propósito
- `POST /profiles/{userId}/document` y los flujos con Azure Blob: subir archivos reales en una prueba de
  carga masiva puede generar costos de almacenamiento no controlados. Si se necesita medirlo, se recomienda
  un escenario aparte con un volumen fijo y pequeño de archivos, corrido manualmente.
- Login con Google/GitHub: dependen de servicios OAuth externos reales; no tiene sentido cargarlos con
  usuarios sintéticos.

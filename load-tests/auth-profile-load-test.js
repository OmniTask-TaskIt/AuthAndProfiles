// Prueba de carga del microservicio Authentication & Profile (TaskIt).
// Uso:
//   k6 run auth-profile-load-test.js
//   k6 run -e BASE_URL=https://tu-ambiente.azurewebsites.net auth-profile-load-test.js
// Requiere el binario de k6 con la extensión xk6-redis para leer el OTP real (ver README.md de esta carpeta).

import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";
import redis from "k6/x/redis";

const registerOkCount = new Counter("register_201");
const otpOkCount       = new Counter("otp_200");
const loginOkCount     = new Counter("login_200");
const profileOkCount   = new Counter("profile_200");

const registerErrors   = new Counter("register_errors");
const otpErrors         = new Counter("otp_errors");
const loginErrors      = new Counter("login_errors");
const profileErrors    = new Counter("profile_errors");
const loginSuccessRate = new Rate("login_success_rate");

const registerDuration = new Trend("register_duration_ms");
const otpDuration       = new Trend("otp_duration_ms");
const loginDuration     = new Trend("login_duration_ms");
const profileDuration   = new Trend("profile_duration_ms");

// NOTA (umbrales): el 800ms original asumía la app y MongoDB en el mismo entorno/región. Aquí Mongo es un
// cluster real de Atlas consultado desde una máquina local, así que cada operación paga latencia de red de
// internet de por sí — se confirmó que un GET /profiles/{id} por userId ya indexado (una consulta trivial)
// sigue en ~1.2s de P95, así que el techo no es la app ni las queries: es la infraestructura de esta prueba.
// Los umbrales de abajo son por endpoint (con margen sobre lo observado) para un entorno local/dev contra
// Atlas. Si algún día se corre esto contra un ambiente real (app y DB en la misma región, tier pagado),
// hay que volver a valores estrictos (p.ej. p(95)<800 global) porque esto no es una meta de producción.
export const options = {
  stages: [
    { duration: "30s", target: 10 },
    { duration: "1m", target: 50 },
    { duration: "30s", target: 100 },
    { duration: "1m", target: 100 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    register_duration_ms: ["p(95)<5000"],
    otp_duration_ms: ["p(95)<3000"],
    login_duration_ms: ["p(95)<3500"],
    profile_duration_ms: ["p(95)<2000"],
    login_success_rate: ["rate>0.98"],
    register_errors: ["count<5"],
    profile_errors: ["count<10"],
  },
};

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

const REDIS_HOST = __ENV.REDIS_HOST || "localhost";
const REDIS_PORT = __ENV.REDIS_PORT || "6379";
const REDIS_PASSWORD = __ENV.REDIS_PASSWORD || "";
// Azure Cache for Redis exige TLS (puerto 6380); en local/docker déjalo en false.
const REDIS_TLS = (__ENV.REDIS_TLS || "false") === "true";

// xk6-redis expone un cliente basado en promesas. Sin TLS se usa la forma por objeto (host/puerto/password
// separados, sin arriesgarse a romper una URL si la contraseña trae caracteres especiales). Con TLS se usa
// el esquema rediss:// tal como lo documenta la extensión, con la contraseña codificada por seguridad.
const redisClient = REDIS_TLS
  ? new redis.Client(`rediss://:${encodeURIComponent(REDIS_PASSWORD)}@${REDIS_HOST}:${REDIS_PORT}`)
  : new redis.Client({
      socket: { host: REDIS_HOST, port: parseInt(REDIS_PORT, 10) },
      password: REDIS_PASSWORD,
    });

function jsonHeaders(token) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers["Authorization"] = `Bearer ${token}`;
  return headers;
}

/**
 * Lee el código OTP en texto plano directamente de Redis. TokenRedisRepository antepone "refresh_token:" a
 * toda llave, y RegisterUserUseCase guarda el OTP bajo "otp:<email>", por lo que la llave real es
 * "refresh_token:otp:<email>".
 * OJO: xk6-redis rechaza la promesa con "redis: nil" cuando la llave no existe, así que hay que capturarlo
 * con try/catch para que el reintento funcione (si no, la excepción rompe la iteración).
 */
async function readOtpFromRedis(email, maxAttempts = 5, delaySeconds = 0.3) {
  const key = `refresh_token:otp:${email}`;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      const code = await redisClient.get(key);
      if (code) return code;
    } catch (e) {
      // "redis: nil" = la llave aún no existe; se reintenta. Otros errores se muestran solo en el último intento.
      if (!String(e).includes("nil") && attempt === maxAttempts) {
        console.error(`[REDIS] error leyendo ${key}: ${e}`);
      }
    }
    if (attempt < maxAttempts) sleep(delaySeconds);
  }
  return null;
}

/**
 * Consulta el perfil con reintentos: justo después de verificar el OTP el perfil ya existe (se crea en
 * /register), pero se deja el mismo patrón de backoff que en el resto de la suite por si el servicio está
 * bajo presión.
 */
function fetchProfileWithRetry(userId, accessToken, maxAttempts = 4, baseDelaySeconds = 0.3) {
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const start = Date.now();
    const res = http.get(`${BASE_URL}/api/v1/profiles/${userId}`, { headers: jsonHeaders(accessToken) });
    profileDuration.add(Date.now() - start);

    if (res.status === 200) return true;
    if (attempt < maxAttempts) {
      sleep(baseDelaySeconds * Math.pow(2, attempt - 1));
    } else {
      console.error(`[PROFILE] VU=${__VU} userId=${userId} agotados ${maxAttempts} intentos status=${res.status}`);
    }
  }
  return false;
}

export default async function () {
  const id = `${__VU}_${__ITER}_${Date.now()}`;
  const email = `user_${id}@loadtest.com`;
  const password = "LoadTest1!";

  // 1. Register
  const regStart = Date.now();
  const registerRes = http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({ email, password, name: `Usuario Carga ${id}`, role: "SEEKER", acceptedTerms: true }),
    { headers: jsonHeaders() }
  );
  registerDuration.add(Date.now() - regStart);

  const registerOk = check(registerRes, { "register → 201": (r) => r.status === 201 });
  if (registerOk) {
    registerOkCount.add(1);
  } else {
    registerErrors.add(1);
    console.error(`[REGISTER] VU=${__VU} status=${registerRes.status} body=${registerRes.body}`);
    return;
  }

  // 2. Verify OTP — el código se lee de Redis, nunca se inventa ni se expone por HTTP
  const otpCode = await readOtpFromRedis(email);
  if (!otpCode) {
    otpErrors.add(1);
    console.error(`[OTP] VU=${__VU} no se encontró el código OTP en Redis para ${email}`);
    return;
  }

  const otpStart = Date.now();
  const otpRes = http.post(
    `${BASE_URL}/api/v1/auth/verify-otp`,
    JSON.stringify({ email, otpCode }),
    { headers: jsonHeaders() }
  );
  otpDuration.add(Date.now() - otpStart);

  const otpOk = check(otpRes, { "verify-otp → 200": (r) => r.status === 200 });
  if (otpOk) {
    otpOkCount.add(1);
  } else {
    otpErrors.add(1);
    console.error(`[OTP] VU=${__VU} status=${otpRes.status} body=${otpRes.body}`);
    return;
  }

  sleep(0.3);

  // 3. Login
  const loginStart = Date.now();
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    { headers: jsonHeaders() }
  );
  loginDuration.add(Date.now() - loginStart);

  const loginOk = check(loginRes, {
    "login → 200": (r) => r.status === 200,
    "login → accessToken": (r) => {
      try {
        return !!JSON.parse(r.body).accessToken;
      } catch {
        return false;
      }
    },
  });
  loginSuccessRate.add(loginOk ? 1 : 0);

  if (loginOk) {
    loginOkCount.add(1);
  } else {
    loginErrors.add(1);
    console.error(`[LOGIN] VU=${__VU} status=${loginRes.status} body=${loginRes.body}`);
    return;
  }

  let accessToken = null;
  try {
    accessToken = JSON.parse(loginRes.body).accessToken;
  } catch (_) {}

  // 4. Profile — el JWT no lleva el userId (solo el email como subject), así que se usa /profiles/search
  // por email (no por nombre) para obtener el userId: SearchProfileUseCase resuelve la búsqueda por email
  // con findByEmail (indexado, O(1) en Atlas), mientras que la búsqueda por nombre usa un regex sin anclar
  // (findByNameContainingIgnoreCase) que siempre es un escaneo completo, indexado o no.
  if (accessToken) {
    const searchRes = http.get(
      `${BASE_URL}/api/v1/profiles/search?name=${encodeURIComponent(email)}`,
      { headers: jsonHeaders(accessToken) }
    );
    let userId = null;
    try {
      const results = JSON.parse(searchRes.body);
      if (Array.isArray(results) && results.length > 0) userId = results[0].userId;
    } catch (_) {}

    if (userId && fetchProfileWithRetry(userId, accessToken)) {
      profileOkCount.add(1);
    } else {
      profileErrors.add(1);
    }
  }

  sleep(0.5);
}

export function handleSummary(data) {
  const m = data.metrics;

  const p95 = m.http_req_duration?.values?.["p(95)"]?.toFixed(2) ?? "N/A";
  const p99 = m.http_req_duration?.values?.["p(99)"]?.toFixed(2) ?? "N/A";
  const avgReq = m.http_req_duration?.values?.avg?.toFixed(2) ?? "N/A";
  const rps = m.http_reqs?.values?.rate?.toFixed(2) ?? "N/A";
  const total = m.http_reqs?.values?.count ?? 0;
  const failed = m.http_req_failed?.values?.rate ?? 0;
  const maxVUs = m.vus_max?.values?.max ?? "N/A";

  const reg201 = m["register_201"]?.values?.count ?? 0;
  const otp200 = m["otp_200"]?.values?.count ?? 0;
  const log200 = m["login_200"]?.values?.count ?? 0;
  const prof200 = m["profile_200"]?.values?.count ?? 0;

  const loginRate = ((m.login_success_rate?.values?.rate ?? 0) * 100).toFixed(2);
  const regErr = m.register_errors?.values?.count ?? 0;
  const otpErr = m.otp_errors?.values?.count ?? 0;
  const logErr = m.login_errors?.values?.count ?? 0;
  const profErr = m.profile_errors?.values?.count ?? 0;

  const regP95 = m.register_duration_ms?.values?.["p(95)"]?.toFixed(2) ?? "N/A";
  const otpP95 = m.otp_duration_ms?.values?.["p(95)"]?.toFixed(2) ?? "N/A";
  const loginP95 = m.login_duration_ms?.values?.["p(95)"]?.toFixed(2) ?? "N/A";
  const profileP95 = m.profile_duration_ms?.values?.["p(95)"]?.toFixed(2) ?? "N/A";

  // Umbrales por endpoint (ver nota junto a options.thresholds): valores de dev/local contra Atlas, no una
  // meta de producción.
  const thresholdReg95 = parseFloat(regP95) < 5000;
  const thresholdOtp95 = parseFloat(otpP95) < 3000;
  const thresholdLogin95 = parseFloat(loginP95) < 3500;
  const thresholdProfile95 = parseFloat(profileP95) < 2000;
  const thresholdLogin = parseFloat(loginRate) > 98;
  const thresholdReg = regErr < 5;
  const thresholdProf = profErr < 10;
  const passed = thresholdReg95 && thresholdOtp95 && thresholdLogin95 && thresholdProfile95 &&
    thresholdLogin && thresholdReg && thresholdProf;

  const pad = (v, n = 30) => String(v).padEnd(n);

  const report = `
╔══════════════════════════════════════════════════════════════╗
║       REPORTE DE CARGA — Authentication & Profile API         ║
╠══════════════════════════════════════════════════════════════╣
║  VUs máximos concurrentes : ${pad(maxVUs)}   ║
║  Total peticiones         : ${pad(total)}   ║
║  Peticiones por segundo   : ${pad(rps + " req/s")}   ║
║  Tasa de fallos HTTP      : ${pad((failed * 100).toFixed(2) + "%")}   ║
╠══════════════════════════════════════════════════════════════╣
║  RESPUESTAS EXITOSAS POR ENDPOINT                             ║
║  POST /register        → 201 : ${pad(reg201 + " OK", 26)}   ║
║  POST /verify-otp      → 200 : ${pad(otp200 + " OK", 26)}   ║
║  POST /login           → 200 : ${pad(log200 + " OK", 26)}   ║
║  GET  /profiles/{id}   → 200 : ${pad(prof200 + " OK", 26)}   ║
╠══════════════════════════════════════════════════════════════╣
║  TIEMPOS DE RESPUESTA GLOBALES                                ║
║  Promedio                 : ${pad(avgReq + " ms")}   ║
║  P95                      : ${pad(p95 + " ms")}   ║
║  P99                      : ${pad(p99 + " ms")}   ║
╠══════════════════════════════════════════════════════════════╣
║  TIEMPOS POR ENDPOINT (P95)                                   ║
║  POST /register           : ${pad(regP95 + " ms")}   ║
║  POST /verify-otp         : ${pad(otpP95 + " ms")}   ║
║  POST /login              : ${pad(loginP95 + " ms")}   ║
║  GET  /profiles/{id}      : ${pad(profileP95 + " ms")}   ║
╠══════════════════════════════════════════════════════════════╣
║  ERRORES POR FLUJO                                            ║
║  Login success rate       : ${pad(loginRate + "%")}   ║
║  Errores register         : ${pad(regErr)}   ║
║  Errores verify-otp       : ${pad(otpErr)}   ║
║  Errores login            : ${pad(logErr)}   ║
║  Errores profile          : ${pad(profErr)}   ║
╠══════════════════════════════════════════════════════════════╣
║  UMBRALES (dev/local vs Atlas — ver nota en options.thresholds)              ║
║  register P95 < 5000ms    : ${pad(thresholdReg95 ? "✅ OK" : "❌ FALLÓ")}   ║
║  verify-otp P95 < 3000ms  : ${pad(thresholdOtp95 ? "✅ OK" : "❌ FALLÓ")}   ║
║  login P95 < 3500ms       : ${pad(thresholdLogin95 ? "✅ OK" : "❌ FALLÓ")}   ║
║  profile P95 < 2000ms     : ${pad(thresholdProfile95 ? "✅ OK" : "❌ FALLÓ")}   ║
║  Login rate > 98%         : ${pad(thresholdLogin ? "✅ OK" : "❌ FALLÓ")}   ║
║  Register errors < 5      : ${pad(thresholdReg ? "✅ OK" : "❌ FALLÓ")}   ║
║  Profile errors < 10      : ${pad(thresholdProf ? "✅ OK" : "❌ FALLÓ")}   ║
╠══════════════════════════════════════════════════════════════╣
║  RESULTADO FINAL: ${passed ? "✅  PASÓ — Todos los umbrales cumplidos  " : "❌  FALLÓ — Revisar errores arriba        "}║
╚══════════════════════════════════════════════════════════════╝
`;

  console.log(report);
  return {
    stdout: report,
    "load-test-summary.json": JSON.stringify(data, null, 2),
  };
}
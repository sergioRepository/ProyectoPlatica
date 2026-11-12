// Prueba de carga del backend con k6. Ver backend/loadtest/README.md antes de lanzarla.
//
//   docker run --rm -i -e BASE_URL -e ENROLLMENT_CODE -e MODE grafana/k6:1.0.0 run - < backend/loadtest/load.js
//
// MODE=smoke: 5 peticiones/s durante 1 minuto, sin IA. MODE=full: escalones hasta que falle,
// más 2 usuarios que piden emergencias reales a Peel. Todos los dispositivos se borran al final.
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL;
const MODE = __ENV.MODE || 'smoke';
const POOL = MODE === 'full' ? 200 : 20;
const PEEL_DEVICES = MODE === 'full' ? 6 : 0;
const CONSENT_VERSION = '2026-11-v3';

const status429 = new Counter('status_429');
const status5xx = new Counter('status_5xx');

const usage = MODE === 'full'
  ? {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 600,
      // 90 s por escalón: ~28.000 peticiones si llega al final (presupuesto del plan Hobby).
      stages: [5, 10, 20, 40, 80, 160].flatMap((rate) => [
        { target: rate, duration: '10s' },
        { target: rate, duration: '80s' },
      ]),
      exec: 'use',
    }
  : { executor: 'constant-arrival-rate', rate: 5, timeUnit: '1s', duration: '1m', preAllocatedVUs: 10, exec: 'use' };

export const options = {
  setupTimeout: '5m',
  teardownTimeout: '5m',
  scenarios: MODE === 'full'
    ? {
        usage,
        peel: { executor: 'constant-vus', vus: 2, duration: '2m', startTime: '30s', exec: 'peel' },
      }
    : { usage },
  thresholds: {
    // Se corta sola si falla más del 5 % o si p95 pasa de 3 s (rompió).
    'http_req_failed{scenario:usage}': [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '20s' }],
    'http_req_duration{scenario:usage}': [{ threshold: 'p(95)<3000', abortOnFail: true, delayAbortEval: '20s' }],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const json = (token) => ({
  headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
});

function track(res, name) {
  if (res.status === 429) status429.add(1, { name });
  if (res.status >= 500) status5xx.add(1, { name });
  return res;
}

function register(excuses) {
  const body = {
    consents: { excuses, phone_usage: true, cross_border: true, analytics: true, text_version: CONSENT_VERSION },
    enrollment_code: __ENV.ENROLLMENT_CODE || null,
  };
  const res = http.post(`${BASE}/devices/register`, JSON.stringify(body), { ...json(), tags: { name: 'register' } });
  if (!check(res, { 'registro 200': (r) => r.status === 200 })) {
    throw new Error(`registro falló: ${res.status} ${res.body}`);
  }
  return res.json('device_token');
}

export function setup() {
  if (!/^(https:\/\/|http:\/\/(localhost|host\.docker\.internal)[:/])/.test(BASE || '')) {
    throw new Error('BASE_URL debe ser https://… o http://host.docker.internal:…');
  }
  const devices = [];
  for (let i = 0; i < POOL; i++) devices.push(register(false));
  const peel = [];
  for (let i = 0; i < PEEL_DEVICES; i++) peel.push(register(true));
  return { devices, peel };
}

function uuid() {
  const hex = () => Math.floor(Math.random() * 16).toString(16);
  return 'xxxxxxxx-xxxx-4xxx-8xxx-xxxxxxxxxxxx'.replace(/x/g, hex);
}

// Mezcla de un usuario real: abrir la app (estado), enviar eventos, a veces tocar Ajustes.
export function use(data) {
  const token = data.devices[Math.floor(Math.random() * data.devices.length)];
  const roll = Math.random();
  let res;
  let name;
  if (roll < 0.6) {
    name = 'devices_me';
    res = http.get(`${BASE}/devices/me`, { ...json(token), tags: { name } });
  } else if (roll < 0.95) {
    name = 'events';
    const events = [{ id: uuid(), name: 'block_shown', app: 'com.zhiliaoapp.musically', occurred_at: new Date().toISOString(), props: { attempt: 1 } }];
    res = http.post(`${BASE}/events`, JSON.stringify({ events }), { ...json(token), tags: { name } });
  } else {
    name = 'consents';
    const consents = { excuses: false, phone_usage: true, cross_border: true, analytics: true, text_version: CONSENT_VERSION };
    res = http.put(`${BASE}/devices/me/consents`, JSON.stringify(consents), { ...json(token), tags: { name } });
  }
  track(res, name);
  check(res, { '2xx': (r) => r.status >= 200 && r.status < 300 });
}

// IA real con poca carga: emergencias, que no gastan evaluaciones ni créditos.
export function peel(data) {
  const token = data.peel[(exec.vu.idInTest + exec.vu.iterationInScenario) % data.peel.length];
  const body = {
    type: 'emergency',
    reason: 'Mi mamá me escribió que está en urgencias y necesito ver el mensaje completo.',
    app: 'WhatsApp',
    local_time: '15:30',
  };
  const res = track(http.post(`${BASE}/excuses/judge`, JSON.stringify(body), { ...json(token), tags: { name: 'judge' } }), 'judge');
  check(res, { 'juez 200': (r) => r.status === 200 });
  sleep(3);
}

export function teardown(data) {
  let failed = 0;
  for (const token of [...data.devices, ...data.peel]) {
    const res = http.del(`${BASE}/devices/me`, null, { ...json(token), tags: { name: 'delete' } });
    if (res.status !== 204) failed++;
  }
  // Comprueba que el borrado se aplicó.
  const gone = http.get(`${BASE}/devices/me`, { ...json(data.devices[0]), tags: { name: 'check_deleted' } });
  console.log(`Borrado: ${data.devices.length + data.peel.length - failed} dispositivos, ${failed} fallidos; comprobación ${gone.status}`);
}

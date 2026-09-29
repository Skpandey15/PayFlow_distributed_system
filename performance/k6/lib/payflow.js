import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { uuidv4 } from './uuid.js';
import { API, APIS, CURRENCY, PERF_USERS, PERF_USER_PREFIX, PAYERS_PER_USER, READ_RATIO, requireEnv } from './config.js';
import { userToken, treasuryToken } from './auth.js';

export const accepted = new Counter('payments_accepted');
export const rejectedAtApi = new Counter('payments_rejected_at_api');
export const throttled = new Counter('payments_throttled');

function json(token, extraHeaders = {}) {
  return { headers: Object.assign({ Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, extraHeaders) };
}

function openAccount(token, name) {
  const res = http.post(`${API}/api/v1/accounts`, JSON.stringify({ displayName: name, currency: CURRENCY }),
    Object.assign(json(token), { tags: { name: 'setup_open_account' } }));
  if (res.status !== 201) {
    throw new Error(`open account failed: ${res.status} ${res.body}`);
  }
  return res.json('id');
}

function deposit(treasury, accountId, amount) {
  const res = http.post(`${API}/api/v1/accounts/${accountId}/deposits`,
    JSON.stringify({ depositId: uuidv4(), amount, currency: CURRENCY }),
    Object.assign(json(treasury), { tags: { name: 'setup_deposit' } }));
  if (res.status !== 201 && res.status !== 200) {
    throw new Error(`deposit failed: ${res.status} ${res.body}`);
  }
}

/**
 * Creates the synthetic population: PERF_USERS customers, each with PAYERS_PER_USER funded accounts. Every
 * customer pays other customers' accounts, so payees are spread like payers. Returned data carries ids only.
 * opts.hotAccount: one extra account owned by the first user that receives a much larger balance.
 */
export function provision(opts = {}) {
  requireEnv();
  const treasury = treasuryToken();
  const balance = opts.balance || '10000000.00';
  const users = [];
  for (let u = 1; u <= PERF_USERS; u++) {
    const username = `${PERF_USER_PREFIX}${String(u).padStart(3, '0')}`;
    const token = userToken(username);
    const payers = [];
    for (let a = 0; a < PAYERS_PER_USER; a++) {
      const id = openAccount(token, `perf ${username} #${a}`);
      deposit(treasury, id, balance);
      payers.push(id);
    }
    users.push({ username, payers });
  }
  let hot = null;
  if (opts.hotAccount) {
    const token = userToken(users[0].username);
    hot = openAccount(token, 'perf hot account');
    deposit(treasury, hot, opts.hotBalance || '900000000.00');
  }
  return { users, hot, startedAt: new Date().toISOString() };
}

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

// Log-uniform 1.00 .. 2000.00: many small payments, few large ones; always below the fraud high-amount rule.
function amount() {
  const v = Math.exp(Math.random() * Math.log(2000));
  return Math.max(1, v).toFixed(2);
}

/**
 * One customer interaction: create a payment (write) and, for READ_RATIO of iterations, read it back (status
 * check). The HTTP response is the ACCEPTANCE latency only; completion is asynchronous and measured server-side
 * (payflow_saga_completion_seconds).
 */
export function payOnce(data, opts = {}) {
  const user = opts.hot ? data.users[0] : data.users[(__VU - 1) % data.users.length];
  const other = data.users.length > 1 ? pick(data.users.filter((u) => u.username !== user.username)) : user;
  const payer = opts.hot ? data.hot : pick(user.payers);
  const payee = pick(other.payers.filter((p) => p !== payer));
  const token = userToken(user.username);
  const body = JSON.stringify({
    payerAccountId: payer, payeeAccountId: payee, amount: amount(), currency: CURRENCY, method: Math.random() < 0.7 ? 'CARD' : 'BANK_TRANSFER', // USD: UPI is INR-only (it would decline)
    reference: `perf-${__VU}-${__ITER}`,
    checkout: { deviceId: `dev-${__VU}`, ipAddress: '203.0.113.10', countryCode: 'US' },
  });
  const api = APIS[(__VU - 1) % APIS.length];
  const res = http.post(`${api}/api/v1/payments`, body,
    Object.assign(json(token, { 'Idempotency-Key': uuidv4() }), { tags: { name: 'POST /api/v1/payments' } }));
  if (res.status === 201) {
    accepted.add(1);
  } else if (res.status === 429 || res.status === 503) {
    throttled.add(1);
  } else {
    rejectedAtApi.add(1);
  }
  check(res, { 'payment accepted (201)': (r) => r.status === 201 });
  if (res.status === 201 && Math.random() < READ_RATIO) {
    const id = res.json('id');
    const get = http.get(`${api}/api/v1/payments/${id}`, Object.assign(json(token), { tags: { name: 'GET /api/v1/payments/{id}' } }));
    check(get, { 'payment readable (200)': (r) => r.status === 200 });
  }
}

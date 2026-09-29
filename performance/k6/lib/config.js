// Shared configuration for every PayFlow k6 scenario. All values come from the environment (see
// performance/scripts/run.sh); nothing secret is hard-coded.
// One or more instances (comma-separated). Each VU sticks to one instance, like a client behind a load balancer
// with connection reuse. Provisioning uses the first.
export const APIS = (__ENV.PAYFLOW_API || 'http://payflow:8080').split(',').map((s) => s.trim()).filter(Boolean);
export const API = APIS[0];
export const TOKEN_URL = __ENV.PAYFLOW_TOKEN_URL || 'http://keycloak:8081/realms/payflow/protocol/openid-connect/token';
export const PERF_USER_PREFIX = __ENV.PERF_USER_PREFIX || 'perf-';
export const PERF_USERS = parseInt(__ENV.PERF_USERS || '20', 10);
export const PERF_USER_PASSWORD = __ENV.PAYFLOW_PERF_USER_PASSWORD;
export const TREASURY_SECRET = __ENV.PAYFLOW_TREASURY_CLIENT_SECRET;
export const PAYERS_PER_USER = parseInt(__ENV.PAYERS_PER_USER || '5', 10);
export const CURRENCY = 'USD';
// Fraction of iterations that also read the payment back (customer status check). Workload model: 0.5.
export const READ_RATIO = parseFloat(__ENV.READ_RATIO || '0.5');

export function requireEnv() {
  if (!PERF_USER_PASSWORD || !TREASURY_SECRET) {
    throw new Error('PAYFLOW_PERF_USER_PASSWORD and PAYFLOW_TREASURY_CLIENT_SECRET must be set');
  }
}

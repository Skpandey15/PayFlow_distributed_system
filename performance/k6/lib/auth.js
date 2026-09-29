import http from 'k6/http';
import { TOKEN_URL, PERF_USER_PASSWORD, TREASURY_SECRET } from './config.js';

// Access tokens live 300 s. Each VU caches its own token (module scope is per VU) and refreshes it early, so
// long soak tests never send expired tokens and Keycloak sees ~1 login per VU per 4 minutes, not per request.
const REFRESH_AFTER_MS = 240 * 1000;
const cache = {};

function fetchToken(body) {
  const res = http.post(TOKEN_URL, body, { tags: { name: 'keycloak_token' }, responseType: 'text' });
  if (res.status !== 200) {
    throw new Error(`token request failed: ${res.status}`);
  }
  return res.json('access_token');
}

export function userToken(username) {
  const entry = cache[username];
  const now = Date.now();
  if (!entry || now - entry.at > REFRESH_AFTER_MS) {
    cache[username] = {
      at: now,
      token: fetchToken({ grant_type: 'password', client_id: 'payflow-customer-app', username, password: PERF_USER_PASSWORD }),
    };
  }
  return cache[username].token;
}

export function treasuryToken() {
  return fetchToken({ grant_type: 'client_credentials', client_id: 'payflow-treasury', client_secret: TREASURY_SECRET });
}

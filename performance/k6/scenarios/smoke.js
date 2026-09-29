import { provision, payOnce } from '../lib/payflow.js';
export { handleSummary } from '../lib/summary.js';

// CI smoke (PR gate): 1 minute at 5 payments/s. Catches gross regressions, not capacity changes.
// RATE/DUR can be overridden (RATE_SCALE, DURATION) for shorter lab runs; reports always state the values used.
const SCALE = parseFloat(__ENV.RATE_SCALE || '1');
const RATE = (r) => Math.max(1, Math.round(r * SCALE));
const DUR = (d) => __ENV.DURATION || d;

export const options = {
  setupTimeout: '10m',
  discardResponseBodies: false,
  summaryTrendStats: ['min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
  scenarios: {
    payments: { executor: 'constant-arrival-rate', rate: RATE(5), timeUnit: '1s', duration: DUR('1m'), preAllocatedVUs: 10, maxVUs: 50 },
  },
  thresholds: {
    'http_req_duration{name:POST /api/v1/payments}': ['p(95)<500'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  return provision();
}

export default function (data) {
  payOnce(data);
}

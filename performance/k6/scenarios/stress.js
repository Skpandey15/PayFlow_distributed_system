import { provision, payOnce } from '../lib/payflow.js';
export { handleSummary } from '../lib/summary.js';

// STRESS: ramp until the system saturates (open model, so overload shows as latency, errors and dropped iterations, not as a politely slower client).
// RATE/DUR can be overridden (RATE_SCALE, DURATION) for shorter lab runs; reports always state the values used.
const SCALE = parseFloat(__ENV.RATE_SCALE || '1');
const RATE = (r) => Math.max(1, Math.round(r * SCALE));
const DUR = (d) => __ENV.DURATION || d;

export const options = {
  setupTimeout: '10m',
  discardResponseBodies: false,
  summaryTrendStats: ['min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
  scenarios: {
    payments: { executor: 'ramping-arrival-rate', startRate: 10, timeUnit: '1s', preAllocatedVUs: 100, maxVUs: 1500,
      stages: [{ target: 50, duration: '1m' }, { target: 100, duration: '2m' }, { target: 200, duration: '2m' },
               { target: 300, duration: '2m' }, { target: RATE(400), duration: '2m' }, { target: 10, duration: '1m' }] },
  },
  thresholds: {
    'http_req_duration{name:POST /api/v1/payments}': ['p(99)<300'],
  },
};

export function setup() {
  return provision();
}

export default function (data) {
  payOnce(data);
}

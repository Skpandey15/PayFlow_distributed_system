import { provision, payOnce } from '../lib/payflow.js';
export { handleSummary } from '../lib/summary.js';

// HOT ACCOUNT: every payment debits ONE payer account (a merchant payout or corporate treasury account). Contrast with normal (spread over many payers) at the same rate.
// RATE/DUR can be overridden (RATE_SCALE, DURATION) for shorter lab runs; reports always state the values used.
const SCALE = parseFloat(__ENV.RATE_SCALE || '1');
const RATE = (r) => Math.max(1, Math.round(r * SCALE));
const DUR = (d) => __ENV.DURATION || d;

export const options = {
  setupTimeout: '10m',
  discardResponseBodies: false,
  summaryTrendStats: ['min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
  scenarios: {
    payments: { executor: 'ramping-arrival-rate', startRate: 10, timeUnit: '1s', preAllocatedVUs: 50, maxVUs: 600,
      stages: [{ target: 50, duration: '1m' }, { target: 50, duration: '2m' }, { target: 150, duration: '1m' }, { target: 150, duration: '2m' },
               { target: RATE(300), duration: '1m' }, { target: RATE(300), duration: '2m' }, { target: 10, duration: '30s' }] },
  },
  thresholds: {
    'http_req_duration{name:POST /api/v1/payments}': ['p(99)<300'],
  },
};

export function setup() {
  return provision({ hotAccount: true });
}

export default function (data) {
  payOnce(data, { hot: true });
}

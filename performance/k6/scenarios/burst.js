import { provision, payOnce } from '../lib/payflow.js';
export { handleSummary } from '../lib/summary.js';

// BURST: steady 20/s with two 30-second flash spikes to 150/s (7.5x), e.g. a sale or payroll batch.
// RATE/DUR can be overridden (RATE_SCALE, DURATION) for shorter lab runs; reports always state the values used.
const SCALE = parseFloat(__ENV.RATE_SCALE || '1');
const RATE = (r) => Math.max(1, Math.round(r * SCALE));
const DUR = (d) => __ENV.DURATION || d;

export const options = {
  setupTimeout: '10m',
  discardResponseBodies: false,
  summaryTrendStats: ['min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
  scenarios: {
    payments: { executor: 'ramping-arrival-rate', startRate: 20, timeUnit: '1s', preAllocatedVUs: 100, maxVUs: 800,
      stages: [{ target: 20, duration: '1m' }, { target: RATE(150), duration: '5s' }, { target: RATE(150), duration: '30s' },
               { target: 20, duration: '5s' }, { target: 20, duration: '2m' }, { target: RATE(150), duration: '5s' },
               { target: RATE(150), duration: '30s' }, { target: 20, duration: '5s' }, { target: 20, duration: '2m' }] },
  },
  thresholds: {
    // Acceptance SLO (docs/sre/SLI-SLO.md): p99 < 300 ms, >= 99.9% accepted. abortOnFail stays off: a run that
    // breaches the SLO is still evidence and must finish so the report shows where it broke.
    'http_req_duration{name:POST /api/v1/payments}': ['p(99)<300'],
    checks: ['rate>0.999'],
  },
};

export function setup() {
  return provision();
}

export default function (data) {
  payOnce(data);
}

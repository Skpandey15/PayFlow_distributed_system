import { provision, payOnce } from '../lib/payflow.js';
export { handleSummary } from '../lib/summary.js';

// PEAK: ramp from normal (20/s) to the assumed daily peak (50/s = 2.5x), hold, ramp down.
// RATE/DUR can be overridden (RATE_SCALE, DURATION) for shorter lab runs; reports always state the values used.
const SCALE = parseFloat(__ENV.RATE_SCALE || '1');
const RATE = (r) => Math.max(1, Math.round(r * SCALE));
const DUR = (d) => __ENV.DURATION || d;

export const options = {
  setupTimeout: '10m',
  discardResponseBodies: false,
  summaryTrendStats: ['min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
  scenarios: {
    payments: { executor: 'ramping-arrival-rate', startRate: 20, timeUnit: '1s', preAllocatedVUs: 50, maxVUs: 400,
      stages: [{ target: RATE(50), duration: '2m' }, { target: RATE(50), duration: DUR('6m') }, { target: 20, duration: '1m' }] },
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

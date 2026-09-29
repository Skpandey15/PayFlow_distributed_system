import { provision, payOnce } from '../lib/payflow.js';
export { handleSummary } from '../lib/summary.js';

// DEPENDENCY DEGRADATION: normal load for 10 minutes while failures are injected externally (performance/scripts/degrade.sh: slow or failing settlement rail, Kafka stop). Measures the blast radius on acceptance.
// RATE/DUR can be overridden (RATE_SCALE, DURATION) for shorter lab runs; reports always state the values used.
const SCALE = parseFloat(__ENV.RATE_SCALE || '1');
const RATE = (r) => Math.max(1, Math.round(r * SCALE));
const DUR = (d) => __ENV.DURATION || d;

export const options = {
  setupTimeout: '10m',
  discardResponseBodies: false,
  summaryTrendStats: ['min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
  scenarios: {
    payments: { executor: 'constant-arrival-rate', rate: RATE(20), timeUnit: '1s', duration: DUR('10m'), preAllocatedVUs: 20, maxVUs: 400 },
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

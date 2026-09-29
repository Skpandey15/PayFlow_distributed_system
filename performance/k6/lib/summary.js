// Writes the k6 end-of-test summary plus the measurement window (after setup, until the last iteration), so the
// report script queries Prometheus for exactly the loaded period and not the provisioning phase.
export function handleSummary(data) {
  const out = __ENV.K6_OUT_DIR || '/out';
  const window = {
    scenario: __ENV.SCENARIO_NAME || 'unknown',
    loadStartedAt: data.setup_data ? data.setup_data.startedAt : null,
    loadEndedAt: new Date().toISOString(),
    rateScale: __ENV.RATE_SCALE || '1',
    durationOverride: __ENV.DURATION || null,
  };
  const result = {};
  result[`${out}/k6-summary.json`] = JSON.stringify({ window, metrics: data.metrics }, null, 2);
  result.stdout = `\nwindow ${window.loadStartedAt} .. ${window.loadEndedAt}\n` +
    Object.entries(data.metrics)
      .filter(([k]) => k.startsWith('http_req_duration') || k.startsWith('payments_') || k === 'dropped_iterations' || k === 'checks' || k === 'http_reqs' || k === 'iterations')
      .map(([k, v]) => `${k}: ${JSON.stringify(v.values)}`).join('\n') + '\n';
  return result;
}

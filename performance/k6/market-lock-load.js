import http from 'k6/http';
import { check } from 'k6';
import execution from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';

const data = JSON.parse(open(__ENV.DATA_FILE || './data/market-lock-test-data.json'));
const base = __ENV.BASE_URL;
if (!base) throw new Error('BASE_URL is required');
const distribution = __ENV.DISTRIBUTION || 'single';
if (!['single', 'spread'].includes(distribution)) throw new Error('DISTRIBUTION must be single or spread');
const marketPool = distribution === 'spread' ? data.spreadMarkets : [data.markets[2]];
if (!marketPool || (distribution === 'spread' && marketPool.length !== 20)) throw new Error('Expected 20 prepared markets');
const readDuration = new Trend('read_duration', true);
const writeDuration = new Trend('write_duration', true);
const successfulWriteDuration = new Trend('successful_write_duration', true);
const failedWriteDuration = new Trend('failed_write_duration', true);
const attemptedWrites = new Counter('attempted_write_requests');
const boundary = 'FleaFleaRealWriteBoundary';
const busy = new Counter('lock_busy_responses');
const otherErrors = new Counter('other_error_responses');
const unavailable = new Counter('service_unavailable_responses');
const internalErrors = new Counter('internal_server_error_responses');
const writes = new Counter('successful_write_requests');
const reads = new Counter('successful_read_requests');

export const options = {
  scenarios: { scenario: { executor: 'constant-arrival-rate', rate: Number(__ENV.RATE || 100), timeUnit: '1s', preAllocatedVUs: 100, maxVUs: 1000, duration: '60s', gracefulStop: '15s' } },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
  thresholds: { checks: ['rate>0.99'], dropped_iterations: ['count==0'] },
};

export default function () {
  const iteration = execution.scenario.iterationInTest;
  const isWrite = iteration % 5 === 0;
  const id = marketPool[Math.floor(iteration / 5) % marketPool.length].marketId;
  const headers = { Authorization: `Bearer ${data.accounts[0].token}` };
  let response;
  if (isWrite) {
    headers['Content-Type'] = `multipart/form-data; boundary=${boundary}`;
    attemptedWrites.add(1);
    const title = `market-update-${__ENV.RUN_ID || execution.scenario.name}-${iteration}`;
    const body = `--${boundary}\r\nContent-Disposition: form-data; name="title"\r\n\r\n${title}\r\n--${boundary}--\r\n`;
    response = http.patch(`${base}/api/v1/markets/${id}`, body, { headers, tags: { name: 'real_market_update' } });
  } else {
    response = http.get(`${base}/api/v1/markets/${id}${iteration % 2 ? '' : '/members'}`, { headers, tags: { name: 'market_read' } });
  }
  (isWrite ? writeDuration : readDuration).add(response.timings.duration);
  if (isWrite) (response.status === 200 ? successfulWriteDuration : failedWriteDuration).add(response.timings.duration);
  const ok = check(response, { 'request completed with 200': result => result.status === 200 });
  if (ok) (isWrite ? writes : reads).add(1);
  if (response.status === 409) busy.add(1);
  if (response.status === 503) unavailable.add(1);
  if (response.status === 500) internalErrors.add(1);
  if (![200, 409, 503, 500].includes(response.status)) otherErrors.add(1);
}

export function handleSummary(summary) {
  return { [__ENV.SUMMARY_PATH || 'stdout']: JSON.stringify(summary, null, 2) };
}

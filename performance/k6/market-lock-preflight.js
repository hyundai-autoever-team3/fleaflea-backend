import http from 'k6/http';
import { check } from 'k6';
const data = JSON.parse(open(__ENV.DATA_FILE || './data/market-lock-test-data.json'));
export const options = { vus: 1, iterations: 1, thresholds: { checks: ['rate==1'] } };
export default function () {
  const boundary = 'FleaFleaRealWriteBoundary';
  const id = data.markets[2].marketId;
  const title = `market-write-verification-${__ENV.RUN_ID || Date.now()}`;
  const headers = { Authorization: `Bearer ${data.accounts[0].token}`, 'Content-Type': `multipart/form-data; boundary=${boundary}` };
  const body = `--${boundary}\r\nContent-Disposition: form-data; name="title"\r\n\r\n${title}\r\n--${boundary}--\r\n`;
  const update = http.patch(`${__ENV.BASE_URL}/api/v1/markets/${id}`, body, { headers });
  delete headers['Content-Type'];
  const read = http.get(`${__ENV.BASE_URL}/api/v1/markets/${id}`, { headers });
  const valid = check(read, { 'written title is returned': result => update.status === 200 && result.status === 200 && result.json().title === title });
  if (!valid) throw new Error('Actual market write verification failed');
  console.log('ACTUAL_MARKET_WRITE_VERIFIED');
}

import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const requesterToken = __ENV.REQUESTER_TOKEN;
const ownerToken = __ENV.OWNER_TOKEN;
const firstItemId = Number(__ENV.FIRST_ITEM_ID || 1001);

if (!requesterToken || !ownerToken) throw new Error('REQUESTER_TOKEN and OWNER_TOKEN are required');

export const options = {
  vus: Number(__ENV.VUS || 1),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

function headers(token) {
  return { headers: { Authorization: `Bearer ${token}` } };
}

export default function () {
  const itemId = firstItemId + exec.scenario.iterationInTest;
  const created = http.post(
    `${baseUrl}/api/v1/collection-items/${itemId}/trade-requests`,
    JSON.stringify({ tradeType: 'RENTAL' }),
    {
      ...headers(requesterToken),
      headers: { ...headers(requesterToken).headers, 'Content-Type': 'application/json' },
      tags: { name: 'collection_trade_create' },
    }
  );
  if (!check(created, { 'create 201': (r) => r.status === 201 })) return;

  const id = created.json('collectionTradeRequestId');
  if (!id) throw new Error('Trade request ID missing from create response');

  const branch = exec.scenario.iterationInTest % 3;
  const action = branch === 0 ? 'accept' : branch === 1 ? 'reject' : 'cancel';
  const actor = branch === 2 ? requesterToken : ownerToken;
  const changed = http.post(`${baseUrl}/api/v1/collection-trade-requests/${id}/${action}`, null, {
    ...headers(actor),
    tags: { name: `collection_trade_${action}` },
  });
  if (!check(changed, { [`${action} 200`]: (r) => r.status === 200 })) return;

  if (branch === 0) {
    const completed = http.post(`${baseUrl}/api/v1/collection-trade-requests/${id}/complete`, null, {
      ...headers(ownerToken),
      tags: { name: 'collection_trade_complete' },
    });
    check(completed, { 'complete 200': (r) => r.status === 200 });
  }

  sleep(1);
}

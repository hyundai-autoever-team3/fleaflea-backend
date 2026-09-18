import http from 'k6/http';
import { check, sleep } from 'k6';
import encoding from 'k6/encoding';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const hostToken = __ENV.OWNER_TOKEN;
const memberToken = __ENV.REQUESTER_TOKEN;
const thinkTime = Number(__ENV.THINK_TIME ?? 1);
const png = encoding.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lXcAAAAASUVORK5CYII=', 'std', 'binary');

if (!hostToken || !memberToken) throw new Error('OWNER_TOKEN and REQUESTER_TOKEN are required');

export const options = {
  vus: Number(__ENV.VUS || 1),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'], checks: ['rate>0.99'],
    ...Object.fromEntries(['create', 'join', 'update', 'invitation_reissue', 'leave', 'delete']
      .map((action) => [`http_req_duration{name:market_${action}}`, ['p(95)<1000']])),
  },
};

function request(method, path, token, body, name, json = false) {
  return http.request(method, `${baseUrl}${path}`, body, {
    headers: { Authorization: `Bearer ${token}`, ...(json ? { 'Content-Type': 'application/json' } : {}) },
    tags: { name },
  });
}

export default function () {
  const created = request('POST', '/api/v1/markets', hostToken, {
    title: 'k6 market',
    description: 'local performance test',
    coverImage: http.file(png, 'k6.png', 'image/png'),
  }, 'market_create');
  if (!check(created, { 'create 201': (r) => r.status === 201 })) return;

  const id = created.json('marketId');
  const inviteCode = created.json('inviteCode');
  if (!id || !inviteCode) throw new Error('marketId or inviteCode missing');

  const joined = request('POST', '/api/v1/market-members', memberToken,
    JSON.stringify({ inviteCode }), 'market_join', true);
  check(joined, { 'join 201': (r) => r.status === 201 });

  const updated = request('PATCH', `/api/v1/markets/${id}`, hostToken, {
    title: 'k6 market updated',
    coverImage: http.file(png, 'k6-update.png', 'image/png'),
  }, 'market_update');
  check(updated, { 'update 200': (r) => r.status === 200 });

  const reissued = request('POST', `/api/v1/markets/${id}/invitation`, hostToken, null, 'market_invitation_reissue');
  check(reissued, { 'reissue 200': (r) => r.status === 200 });

  if (joined.status === 201) {
    const left = request('DELETE', `/api/v1/markets/${id}/members/me`, memberToken, null, 'market_leave');
    check(left, { 'leave 204': (r) => r.status === 204 });
  }

  const removed = request('DELETE', `/api/v1/markets/${id}`, hostToken, null, 'market_delete');
  check(removed, { 'delete 204': (r) => r.status === 204 });
  if (thinkTime > 0) sleep(thinkTime);
}

import http from 'k6/http';
import { check, sleep } from 'k6';
import encoding from 'k6/encoding';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const token = __ENV.K6_TOKEN;
const thinkTime = Number(__ENV.THINK_TIME ?? 1);
const png = encoding.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lXcAAAAASUVORK5CYII=', 'std', 'binary');

if (!token) throw new Error('K6_TOKEN is required');

export const options = {
  vus: Number(__ENV.VUS || 1),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'], checks: ['rate>0.99'],
    'http_req_duration{name:collection_item_create}': ['p(95)<1000'],
    'http_req_duration{name:collection_item_update}': ['p(95)<1000'],
    'http_req_duration{name:collection_item_delete}': ['p(95)<1000'],
  },
};

function request(method, path, body, name) {
  return http.request(method, `${baseUrl}${path}`, body, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name },
  });
}

export default function () {
  const created = request('POST', '/api/v1/collection-items', {
    title: 'k6 collection item',
    description: 'local performance test',
    isPublic: 'true',
    image: http.file(png, 'k6.png', 'image/png'),
  }, 'collection_item_create');
  if (!check(created, { 'create 201': (r) => r.status === 201 })) return;

  const id = created.json('collectionItemId');
  if (!id) throw new Error('collectionItemId missing');

  const updated = request('PATCH', `/api/v1/collection-items/${id}`, {
    title: 'k6 collection item updated',
    image: http.file(png, 'k6-update.png', 'image/png'),
  }, 'collection_item_update');
  check(updated, { 'update 200': (r) => r.status === 200 });

  const removed = request('DELETE', `/api/v1/collection-items/${id}`, null, 'collection_item_delete');
  check(removed, { 'delete 204': (r) => r.status === 204 });
  if (thinkTime > 0) sleep(thinkTime);
}

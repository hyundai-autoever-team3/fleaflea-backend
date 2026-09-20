// Mixed item workload: several markets, parameterized data, open-model load.
//
// Unlike read-api.js (one fixed endpoint + one fixed ID, useful for isolating a
// single query), this script models how the item APIs are actually used:
//   - every iteration picks a different market / item from an exported dataset
//   - 80% of traffic goes to the 5 large markets, 20% to the long tail
//   - list, detail, search and write run concurrently in their real proportions
//   - load is driven by arrival rate, not by a fixed VU count, so latency does
//     not silently absorb slowdowns (coordinated omission)
//
// PROFILE=smoke|load|stress|soak  TARGET_RPS=<transactions/sec>

import http from 'k6/http';
import { check, sleep } from 'k6';
import encoding from 'k6/encoding';
import { SharedArray } from 'k6/data';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const token = __ENV.K6_TOKEN;
const profile = __ENV.PROFILE || 'load';
const datasetPath = __ENV.DATASET || './data/item-dataset.json';
const targetRps = Number(__ENV.TARGET_RPS || 50);
const hotRatio = Number(__ENV.HOT_RATIO ?? 0.8);
const png = encoding.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lXcAAAAASUVORK5CYII=', 'std', 'binary');

if (!token) throw new Error('K6_TOKEN is required');

// SharedArray keeps one copy in memory instead of one per VU.
const dataset = (key) => new SharedArray(key, () => JSON.parse(open(datasetPath))[key]);
const hotMarkets = dataset('hotMarkets');
const coldMarkets = dataset('coldMarkets');
const hotItems = dataset('hotItems');
const coldItems = dataset('coldItems');

if (!hotMarkets.length || !hotItems.length) {
  throw new Error(`${datasetPath} is empty. Run seed-items-local.sql and dataset-export.sql first.`);
}

// Share of business transactions, not of HTTP requests. The write journey sends
// 3 requests per transaction, the read ones send 1.
const MIX = { browseList: 0.6, viewDetail: 0.3, searchItems: 0.07, writeItem: 0.03 };

const headers = { Authorization: `Bearer ${token}` };

function pick(hot, cold) {
  const pool = Math.random() < hotRatio ? hot : cold;
  return pool[Math.floor(Math.random() * pool.length)];
}

// Most users never leave the first page; a few page deep. Uniform random pages
// would overstate OFFSET cost.
function pickPage() {
  const r = Math.random();
  if (r < 0.8) return 0;
  if (r < 0.95) return 1 + Math.floor(Math.random() * 2);
  return 3 + Math.floor(Math.random() * 20);
}

function stagesFor(target) {
  const steps = {
    smoke: [{ duration: '1m', target: Math.max(1, Math.round(target * 0.1)) }],
    load: [
      { duration: '1m', target },
      { duration: '8m', target },
      { duration: '30s', target: 0 },
    ],
    stress: [
      { duration: '2m', target },
      { duration: '2m', target: target * 2 },
      { duration: '2m', target: target * 3 },
      { duration: '2m', target: target * 4 },
      { duration: '1m', target: 0 },
    ],
    soak: [
      { duration: '2m', target },
      { duration: '30m', target },
      { duration: '1m', target: 0 },
    ],
  };

  if (!(profile in steps)) throw new Error(`Unknown PROFILE: ${profile}`);
  return steps[profile];
}

function scenario(exec, share) {
  const target = Math.max(1, Math.round(targetRps * share));

  return {
    executor: 'ramping-arrival-rate',
    exec,
    startRate: Math.max(1, Math.round(target * 0.2)),
    timeUnit: '1s',
    preAllocatedVUs: Math.max(5, target * 2),
    maxVUs: Math.max(20, target * 20),
    stages: stagesFor(target),
    tags: { scenario: exec },
  };
}

export const options = {
  scenarios: Object.fromEntries(
    Object.entries(MIX).map(([exec, share]) => [exec, scenario(exec, share)])
  ),
  // SLO first, then measure against it. A run that breaches these fails.
  // The stress profile is expected to breach them - that is the signal.
  thresholds: {
    http_req_failed: ['rate<0.001'],
    checks: ['rate>0.99'],
    'http_req_duration{name:item_list}': ['p(95)<300', 'p(99)<800'],
    'http_req_duration{name:item_list_filtered}': ['p(95)<300', 'p(99)<800'],
    'http_req_duration{name:item_detail}': ['p(95)<200', 'p(99)<500'],
    'http_req_duration{name:item_search}': ['p(95)<500'],
    'http_req_duration{name:item_create}': ['p(95)<1000'],
    'http_req_duration{name:item_update}': ['p(95)<1000'],
    'http_req_duration{name:item_delete}': ['p(95)<500'],
    // Arrival-rate executors drop iterations when VUs run out. A dropped
    // iteration is load the server never saw, so latency stays pretty while
    // the system is already saturated.
    dropped_iterations: ['count<1'],
  },
};

// JIT compilation, connection pool fill and page cache warm-up all happen on the
// first requests. Without this the ramp-up phase measures the JVM, not the code.
export function setup() {
  for (let i = 0; i < 100; i++) {
    const marketId = hotMarkets[i % hotMarkets.length];
    http.get(`${baseUrl}/api/v1/markets/${marketId}/items?page=0&size=20`, {
      headers,
      tags: { name: 'warmup' },
    });
    http.get(`${baseUrl}/api/v1/items/${hotItems[i % hotItems.length]}`, {
      headers,
      tags: { name: 'warmup' },
    });
  }
}

export function browseList() {
  const marketId = pick(hotMarkets, coldMarkets);
  const page = pickPage();

  // 30% of list views apply a filter.
  const filtered = Math.random() < 0.3;
  const query = filtered
    ? `page=${page}&size=20&tradeType=SALE&status=AVAILABLE`
    : `page=${page}&size=20`;

  const response = http.get(`${baseUrl}/api/v1/markets/${marketId}/items?${query}`, {
    headers,
    tags: { name: filtered ? 'item_list_filtered' : 'item_list' },
  });

  check(response, { 'list 200': (r) => r.status === 200 });
}

export function viewDetail() {
  const itemId = pick(hotItems, coldItems);

  const response = http.get(`${baseUrl}/api/v1/items/${itemId}`, {
    headers,
    tags: { name: 'item_detail' },
  });

  check(response, { 'detail 200': (r) => r.status === 200 });
}

export function searchItems() {
  const marketId = pick(hotMarkets, coldMarkets);
  // Weighted toward selective keywords; 'item' matches every row.
  const r = Math.random();
  const keyword = r < 0.5 ? 'rare' : r < 0.9 ? 'popular' : 'item';

  const response = http.get(
    `${baseUrl}/api/v1/markets/${marketId}/items?page=0&size=20&keyword=${keyword}`,
    { headers, tags: { name: 'item_search' } }
  );

  check(response, { 'search 200': (r) => r.status === 200 });
}

export function writeItem() {
  const marketId = pick(hotMarkets, coldMarkets);

  const created = http.post(
    `${baseUrl}/api/v1/markets/${marketId}/items`,
    {
      title: 'k6 workload item',
      description: 'local performance test',
      tradeType: 'SALE',
      price: '10000',
      image: http.file(png, 'k6.png', 'image/png'),
    },
    { headers, tags: { name: 'item_create' } }
  );

  if (!check(created, { 'create 201': (r) => r.status === 201 })) return;

  const itemId = created.json('itemId');
  if (!itemId) throw new Error('itemId missing in create response');

  sleep(1); // a real user does not edit the instant the item is created

  const updated = http.patch(
    `${baseUrl}/api/v1/items/${itemId}`,
    {
      title: 'k6 workload item updated',
      image: http.file(png, 'k6-update.png', 'image/png'),
    },
    { headers, tags: { name: 'item_update' } }
  );

  check(updated, { 'update 200': (r) => r.status === 200 });

  const removed = http.del(`${baseUrl}/api/v1/items/${itemId}`, null, {
    headers,
    tags: { name: 'item_delete' },
  });

  check(removed, { 'delete 204': (r) => r.status === 204 });
}

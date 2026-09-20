import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const token = __ENV.K6_TOKEN;
const endpoint = __ENV.ENDPOINT || 'collection-items';
const thinkTime = Number(__ENV.THINK_TIME ?? 1);
const paths = {
  'collection-items': '/api/v1/members/me/collection-items?page=0&size=20',
  'member-collection-items': `/api/v1/members/${__ENV.OWNER_ID || ''}/collection-items?page=0&size=20`,
  'collection-item': `/api/v1/collection-items/${__ENV.COLLECTION_ITEM_ID || ''}`,
  markets: '/api/v1/markets?scope=joined&page=0&size=20',
  market: `/api/v1/markets/${__ENV.MARKET_ID || ''}`,
  'market-members': `/api/v1/markets/${__ENV.MARKET_ID || ''}/members?page=0&size=20`,
  'market-invitation': `/api/v1/markets/${__ENV.MARKET_ID || ''}/invitation`,
  'collection-trade': `/api/v1/collection-trade-requests/${__ENV.TRADE_REQUEST_ID || ''}`,
  items: `/api/v1/markets/${__ENV.MARKET_ID || ''}/items?page=${__ENV.PAGE || 0}&size=20`,
  'items-filter': `/api/v1/markets/${__ENV.MARKET_ID || ''}/items?page=${__ENV.PAGE || 0}&size=20&tradeType=SALE&status=AVAILABLE`,
  'items-keyword': `/api/v1/markets/${__ENV.MARKET_ID || ''}/items?page=0&size=20&keyword=${__ENV.KEYWORD || 'rare'}`,
  item: `/api/v1/items/${__ENV.ITEM_ID || ''}`,
};

if (!token) throw new Error('K6_TOKEN is required. Log in and pass the access token with -e K6_TOKEN=...');
if (!(endpoint in paths)) throw new Error(`Unknown ENDPOINT: ${endpoint}`);
if (endpoint === 'collection-item' && !__ENV.COLLECTION_ITEM_ID) throw new Error('COLLECTION_ITEM_ID is required');
if (endpoint === 'member-collection-items' && !__ENV.OWNER_ID) throw new Error('OWNER_ID is required');
if ((endpoint === 'market' || endpoint === 'market-members' || endpoint === 'market-invitation') && !__ENV.MARKET_ID) throw new Error('MARKET_ID is required');
if (endpoint === 'collection-trade' && !__ENV.TRADE_REQUEST_ID) throw new Error('TRADE_REQUEST_ID is required');
if ((endpoint === 'items' || endpoint === 'items-filter' || endpoint === 'items-keyword') && !__ENV.MARKET_ID) throw new Error('MARKET_ID is required');
if (endpoint === 'item' && !__ENV.ITEM_ID) throw new Error('ITEM_ID is required');

export const options = {
  vus: Number(__ENV.VUS || 1),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const response = http.get(`${baseUrl}${paths[endpoint]}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: endpoint },
  });

  check(response, { 'HTTP 200': (r) => r.status === 200 });
  if (thinkTime > 0) sleep(thinkTime);
}

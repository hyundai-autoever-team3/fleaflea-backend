import http from 'k6/http';
import { check, fail } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TEST_EMAIL = __ENV.TEST_EMAIL || 'paulgks@naver.com';
const TEST_PASSWORD = __ENV.TEST_PASSWORD || 'test1234';

export const options = {
    stages: [
        { duration: '30s', target: 10 },
        { duration: '30s', target: 50 },
        { duration: '30s', target: 100 },
        { duration: '10s', target: 0 },
    ],

    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<1000'],
    },
};

export function setup() {
    const response = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({
            email: TEST_EMAIL,
            password: TEST_PASSWORD,
        }),
        {
            headers: {
                'Content-Type': 'application/json',
            },
        }
    );

    check(response, {
        'setup login status is 200': (r) => r.status === 200,
    });

    if (response.status !== 200) {
        fail(`setup login failed: status=${response.status}, body=${response.body}`);
    }

    return {
        refreshToken: response.json().refreshToken,
    };
}

export default function (data) {
    const response = http.post(
        `${BASE_URL}/api/v1/auth/reissue`,
        JSON.stringify({
            refreshToken: data.refreshToken,
        }),
        {
            headers: {
                'Content-Type': 'application/json',
            },
        }
    );

    check(response, {
        'reissue status is 200': (r) => r.status === 200,
        'accessToken exists': (r) => {
            if (r.status !== 200) return false;

            const body = r.json();
            return !!body.accessToken;
        },
    });
}
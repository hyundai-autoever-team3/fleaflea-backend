import http from 'k6/http';
import { check } from 'k6';

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
        http_req_duration: ['p(95)<3000'],
    },
};

export default function () {
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
        'login status is 200': (r) => r.status === 200,
        'accessToken exists': (r) => {
            if (r.status !== 200) return false;

            const body = r.json();
            return !!body.accessToken;
        },
        'refreshToken exists': (r) => {
            if (r.status !== 200) return false;

            const body = r.json();
            return !!body.refreshToken;
        },
    });
}
import http from "k6/http";
import { check } from "k6";
import execution from "k6/execution";
import { testBaseUrl } from "./guard.js";

const base = testBaseUrl();
const data = JSON.parse(open(__ENV.DATA_FILE));
const marketId = data.markets[2].marketId;
const boundary = "FleaFleaLockComparison";
export const options = {
  insecureSkipTLSVerify: __ENV.TEST_SELF_SIGNED_TLS === "yes",
  vus: 1,
  iterations: Number(__ENV.PAIRS || 100),
  thresholds: { checks: ["rate==1"] },
};

function update(field, value) {
  return {
    method: "PATCH",
    url: `${base}/api/v1/markets/${marketId}`,
    body: `--${boundary}\r\nContent-Disposition: form-data; name="${field}"\r\n\r\n${value}\r\n--${boundary}--\r\n`,
    params: {
      headers: {
        Authorization: `Bearer ${data.accounts[0].token}`,
        "Content-Type": `multipart/form-data; boundary=${boundary}`,
      },
    },
  };
}

export default function () {
  const marker = `${__ENV.RUN_ID || "comparison"}-${execution.scenario.iterationInTest}`;
  const title = `title-${marker}`;
  const description = `description-${marker}`;
  const responses = http.batch([
    update("title", title),
    update("description", description),
  ]);
  const response = http.get(`${base}/api/v1/markets/${marketId}`, {
    headers: { Authorization: `Bearer ${data.accounts[0].token}` },
  });
  check(response, {
    "both writes committed": () =>
      responses.every((result) => result.status === 200),
    "both fields preserved": (result) =>
      result.status === 200 &&
      result.json().title === title &&
      result.json().description === description,
  });
}

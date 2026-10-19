export function testBaseUrl() {
  const base = (__ENV.BASE_URL || "").replace(/\/$/, "");
  const allowed = __ENV.TEST_ORIGIN;
  if (!base || base !== allowed || __ENV.CONFIRM_ISOLATED_TEST !== "yes") {
    throw new Error(
      "Set BASE_URL=TEST_ORIGIN and CONFIRM_ISOLATED_TEST=yes for a dedicated test server",
    );
  }
  if (
    !/^https?:\/\/(127\.0\.0\.1|localhost|10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+)(:\d+)?$/.test(
      base,
    )
  ) {
    throw new Error("Only loopback or private test IPv4 origins are allowed");
  }
  return base;
}

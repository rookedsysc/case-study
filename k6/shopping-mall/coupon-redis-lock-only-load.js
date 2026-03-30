import { createCouponLoadTest } from "./coupon-load-common.js";

const couponRedisLockLoadTest = createCouponLoadTest({
  scenarioName: "coupon_issue_only_redis_lock",
  issueKind: "issue_coupon_redis_lock",
  statisticsKind: "read_coupon_store_statistics_redis_lock",
  issuePath: "/api/coupons/redis-lock",
  verifyStatistics: true,
  configOverrides: {
    storeEventTotalCount: 1000,
    userCount: 1000,
    vus: 100000,
  },
});

export const options = couponRedisLockLoadTest.options;
export const setup = couponRedisLockLoadTest.setup;
export const teardown = couponRedisLockLoadTest.teardown;
export default couponRedisLockLoadTest.default;

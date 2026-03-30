import { createCouponLoadTest } from "./coupon-load-common.js";

const couponRedisLockV2LoadTest = createCouponLoadTest({
  scenarioName: "coupon_issue_only_redis_lock_v2",
  issueKind: "issue_coupon_redis_lock_v2",
  statisticsKind: "read_coupon_store_statistics_redis_lock_v2",
  issuePath: "/api/v2/coupons/redis-lock",
  verifyStatistics: true,
});

export const options = couponRedisLockV2LoadTest.options;
export const setup = couponRedisLockV2LoadTest.setup;
export const teardown = couponRedisLockV2LoadTest.teardown;
export default couponRedisLockV2LoadTest.default;

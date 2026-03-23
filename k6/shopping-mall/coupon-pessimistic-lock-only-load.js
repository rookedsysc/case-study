import { createCouponLoadTest } from "./coupon-kafka-load-common.js";

const couponPessimisticLockLoadTest = createCouponLoadTest({
  scenarioName: "coupon_issue_only_pessimistic_lock",
  issueKind: "issue_coupon_pessimistic_lock",
  statisticsKind: "read_coupon_store_statistics_pessimistic_lock",
  issuePath: "/api/coupons/pessimistic-lock",
  verifyStatistics: true,
});

export const options = couponPessimisticLockLoadTest.options;
export const setup = couponPessimisticLockLoadTest.setup;
export const teardown = couponPessimisticLockLoadTest.teardown;
export default couponPessimisticLockLoadTest.default;

import { createCouponKafkaLoadTest } from "./coupon-load-common.js";

const couponKafkaV3LoadTest = createCouponKafkaLoadTest({
  version: "v3",
  issuePath: "/api/v3/coupons/kafka",
  verifyStatistics: true,
});

export const options = couponKafkaV3LoadTest.options;
export const setup = couponKafkaV3LoadTest.setup;
export const teardown = couponKafkaV3LoadTest.teardown;
export default couponKafkaV3LoadTest.default;

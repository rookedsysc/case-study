import { createCouponKafkaLoadTest } from "./coupon-kafka-load-common.js";

const couponKafkaV4LoadTest = createCouponKafkaLoadTest({
  version: "v4",
  issuePath: "/api/v4/coupons/kafka",
  verifyStatistics: true,
});

export const options = couponKafkaV4LoadTest.options;
export const setup = couponKafkaV4LoadTest.setup;
export const teardown = couponKafkaV4LoadTest.teardown;
export default couponKafkaV4LoadTest.default;

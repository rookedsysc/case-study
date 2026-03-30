import { check } from "k6";
import http from "k6/http";
import { Rate, Trend } from "k6/metrics";
import { createCouponKafkaLoadTest } from "./coupon-load-common.js";

const ISSUE_COUPON_SUCCESS_RATE = new Rate("issue_coupon_success_rate");
const ISSUE_COUPON_FAILURE_RATE = new Rate("issue_coupon_failure_rate");
const ISSUE_COUPON_SUCCESS_DURATION = new Trend(
  "issue_coupon_success_duration",
  true,
);
const ELIGIBILITY_STOCK_AVAILABLE = new Rate("eligibility_stock_available");
const ELIGIBILITY_STOCK_UNAVAILABLE = new Rate("eligibility_stock_unavailable");

const BASE_URL = __ENV.BASE_URL || "http://localhost:38080";
const REQUEST_TIMEOUT = __ENV.REQUEST_TIMEOUT || "10m";
const HEADERS = { "Content-Type": "application/json" };

const basePlan = createCouponKafkaLoadTest({
  version: "v5",
  issuePath: "/api/v5/coupons/kafka",
  verifyStatistics: true,
});

const issueCouponTags = {
  phase: "measure",
  kind: "issue_coupon_kafka_v5",
  name: "POST /api/v5/coupons/kafka",
};
const eligibilityTags = {
  phase: "measure",
  kind: "check_eligibility_kafka_v5",
  name: "GET /api/v5/coupons/kafka/eligibility",
};

function is4xxStatus(status) {
  return status >= 400 && status < 500;
}

function isIssueCouponSuccessStatus(status) {
  return status === 201 || status === 202 || is4xxStatus(status);
}

function parseJson(response) {
  try {
    return JSON.parse(response.body);
  } catch (_error) {
    return null;
  }
}

export const options = basePlan.options;
export const setup = basePlan.setup;
export const teardown = basePlan.teardown;

export default function (data) {
  const userIds = data.userIds;
  const userId = userIds[Math.floor(Math.random() * userIds.length)];

  const eligibilityResponse = http.get(
    `${BASE_URL}/api/v5/coupons/kafka/eligibility?storeId=${data.storeId}&userId=${userId}`,
    {
      headers: HEADERS,
      tags: eligibilityTags,
      timeout: REQUEST_TIMEOUT,
    },
  );

  const eligibility = parseJson(eligibilityResponse);
  const isStockAvailable =
    eligibilityResponse.status === 200 &&
    eligibility !== null &&
    eligibility.eligible;

  ELIGIBILITY_STOCK_AVAILABLE.add(isStockAvailable, eligibilityTags);
  ELIGIBILITY_STOCK_UNAVAILABLE.add(!isStockAvailable, eligibilityTags);

  if (!isStockAvailable) {
    return;
  }

  const response = http.post(
    `${BASE_URL}/api/v5/coupons/kafka`,
    JSON.stringify({ storeId: data.storeId, userId }),
    {
      headers: HEADERS,
      tags: issueCouponTags,
      timeout: REQUEST_TIMEOUT,
      responseCallback: http.expectedStatuses(201, 202, {
        min: 400,
        max: 499,
      }),
    },
  );

  const body = parseJson(response);
  const isSuccessfulIssue = isIssueCouponSuccessStatus(response.status);

  ISSUE_COUPON_SUCCESS_RATE.add(isSuccessfulIssue, issueCouponTags);
  ISSUE_COUPON_FAILURE_RATE.add(!isSuccessfulIssue, issueCouponTags);

  if (isSuccessfulIssue) {
    ISSUE_COUPON_SUCCESS_DURATION.add(
      response.timings.duration,
      issueCouponTags,
    );
  }

  const isSuccessResponse =
    response.status === 201 || response.status === 202;

  check(response, {
    "쿠폰 발행 응답 허용": (result) =>
      isIssueCouponSuccessStatus(result.status),
    "쿠폰 발행 성공 시 ID 존재": () =>
      !isSuccessResponse || (body !== null && body.id !== undefined),
    "쿠폰 발행 성공 시 storeId 일치": () =>
      !isSuccessResponse || (body !== null && body.storeId === data.storeId),
    "쿠폰 발행 성공 시 userId 일치": () =>
      !isSuccessResponse || (body !== null && body.userId === userId),
    "쿠폰 발행 성공 시 issuedAt 존재": () =>
      !isSuccessResponse || (body !== null && body.issuedAt !== undefined),
  });
}

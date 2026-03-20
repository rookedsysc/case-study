import http from "k6/http";
import { check } from "k6";
import exec from "k6/execution";
import { Rate, Trend } from "k6/metrics";

const ISSUE_COUPON_SUCCESS_RATE = new Rate("issue_coupon_success_rate");
const ISSUE_COUPON_FAILURE_RATE = new Rate("issue_coupon_failure_rate");
const ISSUE_COUPON_SUCCESS_DURATION = new Trend(
  "issue_coupon_success_duration",
  true,
);
const ISSUE_COUPON_EXPECTED_RESPONSE = http.expectedStatuses(201, {
  min: 400,
  max: 499,
});

const CONFIG = {
  baseUrl: __ENV.BASE_URL || "http://localhost:38080",
  headers: { "Content-Type": "application/json" },
  // 테스트 데이터를 만들 때 사용할 매장 수입니다.
  storeCount: 1,
  // 한 매장에서 발급 가능한 전체 쿠폰 수량입니다.
  storeEventTotalCount: 1000,
  // 부하를 줄 유저 수입니다.
  userCount: 10000,
  // 유저를 한 번에 생성할 최대 개수입니다.
  bulkCreateLimit: 1000,
  // 동시에 요청을 보내는 가상 사용자 수입니다.
  vus: 100000,
  // 부하를 유지할 시간입니다.
  duration: "5m",
  tags: {
    createStores: { phase: "setup", kind: "create_stores_bulk" },
    createUsers: { phase: "setup", kind: "create_users_bulk" },
    // 실제 성능 측정 대상인 Redis 락 쿠폰 발급 요청 태그입니다.
    issueCoupon: { phase: "measure", kind: "issue_coupon_redis_lock" },
    readStatistics: {
      phase: "verify",
      // 테스트 종료 후 발급 결과 검증에 사용할 통계 조회 태그입니다.
      kind: "read_coupon_store_statistics_redis_lock",
    },
  },
};

export const options = {
  scenarios: {
    // 고정된 VU 수로 전체 테스트 시간 동안 계속 요청을 보냅니다.
    coupon_issue_only_redis_lock: {
      executor: "constant-vus",
      vus: CONFIG.vus,
      duration: CONFIG.duration,
    },
  },
};

function is4xxStatus(status) {
  return status >= 400 && status < 500;
}

function isIssueCouponSuccessStatus(status) {
  return status === 201 || is4xxStatus(status);
}

function parseJson(response) {
  try {
    return JSON.parse(response.body);
  } catch (_error) {
    return null;
  }
}

function postJson(
  path,
  payload,
  tags,
  responseCallback = http.expectedStatuses(201),
) {
  return http.post(`${CONFIG.baseUrl}${path}`, JSON.stringify(payload), {
    headers: CONFIG.headers,
    tags,
    responseCallback,
  });
}

function getJson(path, tags, responseCallback = http.expectedStatuses(200)) {
  return http.get(`${CONFIG.baseUrl}${path}`, {
    headers: CONFIG.headers,
    tags,
    responseCallback,
  });
}

function readIds(response, expectedCount, resourceName) {
  const body = parseJson(response);

  if (
    response.status !== 201 ||
    body === null ||
    !Array.isArray(body.ids) ||
    body.ids.length !== expectedCount
  ) {
    throw new Error(
      `${resourceName} bulk 생성에 실패했습니다 (count=${expectedCount})`,
    );
  }

  return body.ids;
}

function createStoreId() {
  const response = postJson(
    "/api/stores/bulk",
    {
      count: CONFIG.storeCount,
      eventTotalCount: CONFIG.storeEventTotalCount,
    },
    CONFIG.tags.createStores,
  );

  return readIds(response, CONFIG.storeCount, "store")[0];
}

function createUserIds() {
  const userIds = [];

  for (
    let createdUserCount = 0;
    createdUserCount < CONFIG.userCount;
    createdUserCount += CONFIG.bulkCreateLimit
  ) {
    const currentBatchSize = Math.min(
      CONFIG.bulkCreateLimit,
      CONFIG.userCount - createdUserCount,
    );
    const response = postJson(
      "/api/users/bulk",
      { count: currentBatchSize },
      CONFIG.tags.createUsers,
    );

    userIds.push(...readIds(response, currentBatchSize, "user"));
  }

  return userIds;
}

function getAssignedUserId(userIds) {
  if (userIds.length === 0) {
    throw new Error("coupon 발행에 사용할 user가 없습니다");
  }

  const userIndex = Math.floor(Math.random() * userIds.length);
  return userIds[userIndex];
}

export function setup() {
  return {
    storeId: createStoreId(),
    userIds: createUserIds(),
  };
}

export function teardown(data) {
  const response = getJson(
    `/api/coupons/stores/${data.storeId}/statistics`,
    CONFIG.tags.readStatistics,
  );
  const body = parseJson(response);

  const isValid = check(response, {
    "통계 조회 성공": (result) => result.status === 200,
    "통계 응답 storeId 일치": () => body !== null && body.storeId === data.storeId,
    "통계 응답 총 발급 수량 일치": () =>
      body !== null && body.eventTotalCount === CONFIG.storeEventTotalCount,
    "통계 응답 발급 수량이 목표 수량과 일치": () =>
      body !== null && body.issuedCouponCount === CONFIG.storeEventTotalCount,
    "통계 응답 잔여 수량 0": () =>
      body !== null && body.remainingCouponCount === 0,
    "통계 응답 상위 유저 중복 발급 없음": () =>
      body !== null &&
      Array.isArray(body.topIssuedUsers) &&
      body.topIssuedUsers.every((user) => user.issuedCouponCount === 1),
  });

  if (!isValid) {
    throw new Error(
      `쿠폰 통계 검증에 실패했습니다: ${response.status} ${response.body}`,
    );
  }
}

export default function issueCouponOnly(data) {
  const userId = getAssignedUserId(data.userIds);
  const response = postJson(
    "/api/coupons/redis-lock",
    { storeId: data.storeId, userId },
    CONFIG.tags.issueCoupon,
    ISSUE_COUPON_EXPECTED_RESPONSE,
  );
  const body = parseJson(response);
  const isSuccessfulIssue = isIssueCouponSuccessStatus(response.status);

  // 201과 모든 4xx를 성공으로 보고 성공률과 실패율을 각각 기록합니다.
  ISSUE_COUPON_SUCCESS_RATE.add(isSuccessfulIssue, CONFIG.tags.issueCoupon);
  ISSUE_COUPON_FAILURE_RATE.add(!isSuccessfulIssue, CONFIG.tags.issueCoupon);

  // 성공으로 간주한 요청만 응답 시간 통계에 포함합니다.
  if (isSuccessfulIssue) {
    ISSUE_COUPON_SUCCESS_DURATION.add(
      response.timings.duration,
      CONFIG.tags.issueCoupon,
    );
  }

  check(response, {
    "쿠폰 발행 응답 허용": (result) => isIssueCouponSuccessStatus(result.status),
    "쿠폰 발행 성공 시 ID 존재": () =>
      response.status !== 201 || (body !== null && body.id !== undefined),
    "쿠폰 발행 성공 시 storeId 일치": () =>
      response.status !== 201 ||
      (body !== null && body.storeId === data.storeId),
    "쿠폰 발행 성공 시 userId 일치": () =>
      response.status !== 201 || (body !== null && body.userId === userId),
    "쿠폰 발행 성공 시 issuedAt 존재": () =>
      response.status !== 201 || (body !== null && body.issuedAt !== undefined),
  });
}

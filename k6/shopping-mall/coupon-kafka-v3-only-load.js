import { check, sleep } from "k6";
import http from "k6/http";
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
  storeCount: 1,
  storeEventTotalCount: 5000,
  userCount: 20000,
  userPageSize: 2000,
  vus: 4500,
  duration: "5m",
  requestTimeout: __ENV.REQUEST_TIMEOUT || "10m",
  gracefulStop: __ENV.GRACEFUL_STOP || "10m",
  setupTimeout: __ENV.SETUP_TIMEOUT || "10m",
  verifyTimeoutMs: Number(__ENV.VERIFY_TIMEOUT_MS || 120000),
  verifyPollIntervalSeconds: Number(__ENV.VERIFY_POLL_INTERVAL_SECONDS || 2),
  tags: {
    createStores: { phase: "setup", kind: "create_stores_bulk" },
    readUsers: { phase: "setup", kind: "read_existing_user_ids" },
    issueCoupon: { phase: "measure", kind: "issue_coupon_kafka_v3" },
    readStatistics: {
      phase: "verify",
      kind: "read_coupon_store_statistics_kafka_v3",
    },
  },
};

export const options = {
  setupTimeout: CONFIG.setupTimeout,
  scenarios: {
    coupon_issue_only_kafka_v3: {
      executor: "constant-vus",
      vus: CONFIG.vus,
      duration: CONFIG.duration,
      gracefulStop: CONFIG.gracefulStop,
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
    timeout: CONFIG.requestTimeout,
    responseCallback,
  });
}

function getJson(path, tags, responseCallback = http.expectedStatuses(200)) {
  return http.get(`${CONFIG.baseUrl}${path}`, {
    headers: CONFIG.headers,
    tags,
    timeout: CONFIG.requestTimeout,
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
    const responseBody =
      typeof response.body === "string" && response.body.length > 500
        ? `${response.body.slice(0, 500)}...`
        : response.body;
    throw new Error(
      `${resourceName} bulk 생성에 실패했습니다 (count=${expectedCount}, status=${response.status}, body=${responseBody})`,
    );
  }

  return body.ids;
}

function readUserIdsPage(response, requestedCount) {
  const body = parseJson(response);

  if (
    response.status !== 200 ||
    body === null ||
    !Array.isArray(body.ids) ||
    body.ids.length > requestedCount
  ) {
    const responseBody =
      typeof response.body === "string" && response.body.length > 500
        ? `${response.body.slice(0, 500)}...`
        : response.body;
    throw new Error(
      `기존 user ID 조회에 실패했습니다 (requestedCount=${requestedCount}, status=${response.status}, body=${responseBody})`,
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
  let page = 0;

  while (userIds.length < CONFIG.userCount) {
    const currentBatchSize = Math.min(
      CONFIG.userPageSize,
      CONFIG.userCount - userIds.length,
    );
    const response = getJson(
      `/api/users/ids?page=${page}&size=${currentBatchSize}`,
      CONFIG.tags.readUsers,
    );
    const pagedUserIds = readUserIdsPage(response, currentBatchSize);

    if (pagedUserIds.length === 0) {
      throw new Error(
        `기존 user가 부족합니다 (requiredCount=${CONFIG.userCount}, loadedCount=${userIds.length})`,
      );
    }

    userIds.push(...pagedUserIds);
    page += 1;
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

function readCouponStatistics(storeId) {
  const response = getJson(
    `/api/coupons/stores/${storeId}/statistics`,
    CONFIG.tags.readStatistics,
  );

  return {
    response,
    body: parseJson(response),
  };
}

export function setup() {
  return {
    storeId: createStoreId(),
    userIds: createUserIds(),
  };
}

export function teardown(data) {
  const verifyStartedAt = Date.now();
  let statistics = readCouponStatistics(data.storeId);

  while (
    Date.now() - verifyStartedAt < CONFIG.verifyTimeoutMs &&
    statistics.response.status === 200 &&
    statistics.body !== null &&
    statistics.body.issuedCouponCount < CONFIG.storeEventTotalCount
  ) {
    sleep(CONFIG.verifyPollIntervalSeconds);
    statistics = readCouponStatistics(data.storeId);
  }

  const { response, body } = statistics;

  const isValid = check(response, {
    "통계 조회 성공": (result) => result.status === 200,
    "통계 응답 storeId 일치": () =>
      body !== null && body.storeId === data.storeId,
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
    const issuedCouponCount = body !== null ? body.issuedCouponCount : "unknown";
    const remainingCouponCount =
      body !== null ? body.remainingCouponCount : "unknown";
    throw new Error(
      `쿠폰 통계 검증에 실패했습니다 (waitedMs=${Date.now() - verifyStartedAt}, status=${response.status}, issuedCouponCount=${issuedCouponCount}, remainingCouponCount=${remainingCouponCount}, body=${response.body})`,
    );
  }
}

export default function issueCouponOnly(data) {
  const userId = getAssignedUserId(data.userIds);
  const response = postJson(
    "/api/v3/coupons/kafka",
    { storeId: data.storeId, userId },
    CONFIG.tags.issueCoupon,
    ISSUE_COUPON_EXPECTED_RESPONSE,
  );
  const body = parseJson(response);
  const isSuccessfulIssue = isIssueCouponSuccessStatus(response.status);

  ISSUE_COUPON_SUCCESS_RATE.add(isSuccessfulIssue, CONFIG.tags.issueCoupon);
  ISSUE_COUPON_FAILURE_RATE.add(!isSuccessfulIssue, CONFIG.tags.issueCoupon);

  if (isSuccessfulIssue) {
    ISSUE_COUPON_SUCCESS_DURATION.add(
      response.timings.duration,
      CONFIG.tags.issueCoupon,
    );
  }

  check(response, {
    "쿠폰 발행 응답 허용": (result) =>
      isIssueCouponSuccessStatus(result.status),
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

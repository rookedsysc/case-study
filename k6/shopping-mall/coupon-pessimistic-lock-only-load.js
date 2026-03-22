import http from "k6/http";
import { check, sleep } from "k6";
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
  storeEventTotalCount: 5000,
  // setup 단계에서 조회할 기존 유저 수입니다.
  userCount: 20000,
  // 한 번에 조회할 기존 유저 ID 최대 개수입니다.
  userPageSize: 2000,
  // 동시에 요청을 보내는 가상 사용자 수입니다.
  vus: 4500,
  // 부하를 유지할 시간입니다.
  duration: "5m",
  // k6 기본 60초 요청 타임아웃에 걸리지 않도록 충분히 크게 둡니다.
  requestTimeout: __ENV.REQUEST_TIMEOUT || "10m",
  // 종료 시점에도 진행 중인 요청 응답을 최대한 수집하도록 충분히 기다립니다.
  gracefulStop: __ENV.GRACEFUL_STOP || "10m",
  // setup 단계에서 테스트 데이터 생성을 기다릴 최대 시간입니다.
  setupTimeout: __ENV.SETUP_TIMEOUT || "10m",
  // teardown 검증 전에 서버 후처리가 끝날 때까지 대기할 최대 시간(ms)입니다.
  verifyTimeoutMs: Number(__ENV.VERIFY_TIMEOUT_MS || 120000),
  // teardown 통계 재조회 간격(초)입니다.
  verifyPollIntervalSeconds: Number(__ENV.VERIFY_POLL_INTERVAL_SECONDS || 2),
  tags: {
    createStores: { phase: "setup", kind: "create_stores_bulk" },
    readUsers: { phase: "setup", kind: "read_existing_user_ids" },
    // 실제 성능 측정 대상인 비관적 락 쿠폰 발급 요청 태그입니다.
    issueCoupon: { phase: "measure", kind: "issue_coupon_pessimistic_lock" },
    readStatistics: {
      phase: "verify",
      // 테스트 종료 후 발급 결과 검증에 사용할 통계 조회 태그입니다.
      kind: "read_coupon_store_statistics_pessimistic_lock",
    },
  },
};

export const options = {
  setupTimeout: CONFIG.setupTimeout,
  scenarios: {
    // 고정된 VU 수로 전체 테스트 시간 동안 계속 요청을 보냅니다.
    coupon_issue_only_pessimistic_lock: {
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
    "/api/coupons/pessimistic-lock",
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

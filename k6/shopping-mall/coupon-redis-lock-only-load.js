import http from "k6/http";
import { check } from "k6";
import exec from "k6/execution";

const ISSUE_COUPON_EXPECTED_STATUSES = [201, 409, 410];

const CONFIG = {
  baseUrl: __ENV.BASE_URL || "http://localhost:38080",
  headers: { "Content-Type": "application/json" },
  storeCount: 1,
  storeEventTotalCount: 1000,
  userCount: 10000,
  bulkCreateLimit: 1000,
  vus: 100000,
  duration: "5m",
  tags: {
    createStores: { phase: "setup", kind: "create_stores_bulk" },
    createUsers: { phase: "setup", kind: "create_users_bulk" },
    issueCoupon: { phase: "measure", kind: "issue_coupon_redis_lock" },
    readStatistics: {
      phase: "verify",
      kind: "read_coupon_store_statistics_redis_lock",
    },
  },
};

export const options = {
  scenarios: {
    coupon_issue_only_redis_lock: {
      executor: "constant-vus",
      vus: CONFIG.vus,
      duration: CONFIG.duration,
    },
  },
  thresholds: {
    "http_req_failed{phase:measure,kind:issue_coupon_redis_lock}": ["rate < 0.01"],
    "http_req_duration{phase:measure,kind:issue_coupon_redis_lock,status:201}": [
      "p(95) < 500",
    ],
  },
};

function parseJson(response) {
  try {
    return JSON.parse(response.body);
  } catch (_error) {
    return null;
  }
}

function postJson(path, payload, tags, expectedStatuses = [201]) {
  return http.post(`${CONFIG.baseUrl}${path}`, JSON.stringify(payload), {
    headers: CONFIG.headers,
    tags,
    responseCallback: http.expectedStatuses(...expectedStatuses),
  });
}

function getJson(path, tags, expectedStatuses = [200]) {
  return http.get(`${CONFIG.baseUrl}${path}`, {
    headers: CONFIG.headers,
    tags,
    responseCallback: http.expectedStatuses(...expectedStatuses),
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

  const userIndex = (exec.vu.idInTest - 1) % userIds.length;
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
    ISSUE_COUPON_EXPECTED_STATUSES,
  );
  const body = parseJson(response);

  check(response, {
    "쿠폰 발행 응답 허용": (result) =>
      ISSUE_COUPON_EXPECTED_STATUSES.includes(result.status),
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

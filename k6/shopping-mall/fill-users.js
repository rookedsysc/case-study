import http from "k6/http";

const CONFIG = {
  baseUrl: __ENV.BASE_URL || "http://localhost:38080",
  targetUserCount: Number(__ENV.TARGET_USER_COUNT || 20000),
  readPageSize: Math.min(Number(__ENV.READ_PAGE_SIZE || 2000), 2000),
  createBatchSize: Math.min(Number(__ENV.CREATE_BATCH_SIZE || 1000), 1000),
  parallelRequests: Number(__ENV.PARALLEL_REQUESTS || 10),
  requestTimeout: __ENV.REQUEST_TIMEOUT || "2m",
  headers: { "Content-Type": "application/json" },
};

export const options = {
  vus: 1,
  iterations: 1,
};

function parseJson(response) {
  try {
    return JSON.parse(response.body);
  } catch (_error) {
    return null;
  }
}

function getJson(path, tags) {
  return http.get(`${CONFIG.baseUrl}${path}`, {
    headers: CONFIG.headers,
    tags,
    timeout: CONFIG.requestTimeout,
  });
}

function buildPostRequest(path, payload, tags) {
  return [
    "POST",
    `${CONFIG.baseUrl}${path}`,
    JSON.stringify(payload),
    {
      headers: CONFIG.headers,
      tags,
      timeout: CONFIG.requestTimeout,
    },
  ];
}

function readExistingUserCount() {
  let totalUserCount = 0;
  let page = 0;

  while (true) {
    const response = getJson(
      `/api/users/ids?page=${page}&size=${CONFIG.readPageSize}`,
      { phase: "count", kind: "read_user_ids" },
    );
    const body = parseJson(response);

    if (
      response.status !== 200 ||
      body === null ||
      !Array.isArray(body.ids) ||
      body.ids.length > CONFIG.readPageSize
    ) {
      throw new Error(
        `유저 수 조회에 실패했습니다 (page=${page}, status=${response.status}, body=${response.body})`,
      );
    }

    if (body.ids.length === 0) {
      return totalUserCount;
    }

    totalUserCount += body.ids.length;

    if (body.ids.length < CONFIG.readPageSize) {
      return totalUserCount;
    }

    page += 1;
  }
}

function createBatchSizes(remainingUserCount) {
  const batchSizes = [];
  let pendingUserCount = remainingUserCount;

  while (pendingUserCount > 0) {
    const currentBatchSize = Math.min(CONFIG.createBatchSize, pendingUserCount);
    batchSizes.push(currentBatchSize);
    pendingUserCount -= currentBatchSize;
  }

  return batchSizes;
}

function createUsers(batchSizes) {
  let createdUserCount = 0;

  for (
    let startIndex = 0;
    startIndex < batchSizes.length;
    startIndex += CONFIG.parallelRequests
  ) {
    const currentBatchSizes = batchSizes.slice(
      startIndex,
      startIndex + CONFIG.parallelRequests,
    );
    const responses = http.batch(
      currentBatchSizes.map((count) =>
        buildPostRequest(
          "/api/users/bulk",
          { count },
          { phase: "fill", kind: "create_users_bulk" },
        ),
      ),
    );

    responses.forEach((response, index) => {
      const requestedCount = currentBatchSizes[index];
      const body = parseJson(response);

      if (
        response.status !== 201 ||
        body === null ||
        !Array.isArray(body.ids) ||
        body.ids.length !== requestedCount
      ) {
        throw new Error(
          `유저 생성에 실패했습니다 (count=${requestedCount}, status=${response.status}, body=${response.body})`,
        );
      }

      createdUserCount += body.ids.length;
    });
  }

  return createdUserCount;
}

export default function fillUsers() {
  const existingUserCount = readExistingUserCount();
  const remainingUserCount = Math.max(CONFIG.targetUserCount - existingUserCount, 0);

  console.log(
    `existingUserCount=${existingUserCount}, targetUserCount=${CONFIG.targetUserCount}, remainingUserCount=${remainingUserCount}`,
  );

  if (remainingUserCount === 0) {
    console.log("이미 목표 유저 수를 충족했습니다.");
    return;
  }

  const batchSizes = createBatchSizes(remainingUserCount);
  const createdUserCount = createUsers(batchSizes);
  const finalUserCount = existingUserCount + createdUserCount;

  console.log(
    `createdUserCount=${createdUserCount}, finalUserCount=${finalUserCount}, requestBatchCount=${batchSizes.length}`,
  );

  if (finalUserCount < CONFIG.targetUserCount) {
    throw new Error(
      `목표 유저 수를 채우지 못했습니다 (targetUserCount=${CONFIG.targetUserCount}, finalUserCount=${finalUserCount})`,
    );
  }
}

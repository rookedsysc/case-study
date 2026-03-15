package com.roky.casestudy.coupon.exception

/** 캐시 로드를 위한 락 획득이 제한 시간 내에 이루어지지 않은 경우 발생합니다. */
class CouponCacheLoadLockTimeoutException(
    lockKey: String,
) : RuntimeException("캐시 로드 락 획득 타임아웃: $lockKey")

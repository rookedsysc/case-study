package com.roky.casestudy.coupon.exception

import java.util.UUID

/** 사용자 락 해제가 실패한 경우 발생합니다. */
class CouponLockReleaseFailedException(
    userId: UUID,
) : RuntimeException("유저 $userId 의 쿠폰 락 해제에 실패했습니다")

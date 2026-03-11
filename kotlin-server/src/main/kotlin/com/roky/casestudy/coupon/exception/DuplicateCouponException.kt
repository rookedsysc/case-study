package com.roky.casestudy.coupon.exception

import java.util.UUID

/** 유저가 동일 상점의 쿠폰을 이미 보유한 경우 발생합니다. */
class DuplicateCouponException(
    storeId: UUID,
    userId: UUID,
) : RuntimeException("유저 $userId 는 이미 상점 $storeId 의 쿠폰을 보유하고 있습니다")

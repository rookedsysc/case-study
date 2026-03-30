package com.roky.casestudy.coupon.exception

import java.util.UUID

/** 상점의 쿠폰 발행 한도를 초과한 경우 발생합니다. */
class CouponLimitExceededException(
    storeId: UUID,
) : RuntimeException("상점 $storeId 의 쿠폰이 모두 소진되었습니다")

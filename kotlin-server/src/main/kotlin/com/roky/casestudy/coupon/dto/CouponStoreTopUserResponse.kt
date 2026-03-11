package com.roky.casestudy.coupon.dto

import java.util.UUID

/** 상점 기준 상위 쿠폰 발급 유저 응답 */
data class CouponStoreTopUserResponse(
    val userId: UUID,
    val issuedCouponCount: Long,
)

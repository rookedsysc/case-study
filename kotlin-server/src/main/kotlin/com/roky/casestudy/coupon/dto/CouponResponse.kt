package com.roky.casestudy.coupon.dto

import java.time.Instant
import java.util.UUID

/** 쿠폰 응답 */
data class CouponResponse(
    val id: UUID,
    val storeId: UUID,
    val userId: UUID,
    val issuedAt: Instant,
)

package com.roky.casestudy.coupon.dto

import java.time.Instant
import java.util.UUID

/** Kafka 쿠폰 발행 이벤트 페이로드 */
data class CouponIssueEvent(
    val couponId: UUID,
    val storeId: UUID,
    val userId: UUID,
    val issuedAt: Instant,
)

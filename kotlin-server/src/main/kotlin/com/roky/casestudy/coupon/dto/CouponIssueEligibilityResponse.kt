package com.roky.casestudy.coupon.dto

import java.util.UUID

/** 쿠폰 발급 가능 여부 응답 */
data class CouponIssueEligibilityResponse(
    val storeId: UUID,
    val userId: UUID,
    /** 발급 가능 여부 */
    val eligible: Boolean,
    /** 발급 불가 사유. 발급 가능하면 null */
    val reason: CouponIneligibleReason? = null,
)

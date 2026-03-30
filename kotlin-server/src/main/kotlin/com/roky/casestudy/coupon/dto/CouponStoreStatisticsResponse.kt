package com.roky.casestudy.coupon.dto

import java.util.UUID

/** 상점 기준 쿠폰 발급 통계 응답 */
data class CouponStoreStatisticsResponse(
    val storeId: UUID,
    val eventTotalCount: Long,
    val issuedCouponCount: Long,
    val remainingCouponCount: Long,
    val topIssuedUsers: List<CouponStoreTopUserResponse>,
)

package com.roky.casestudy.coupon.dto

import jakarta.validation.constraints.NotNull
import java.util.UUID

/** 쿠폰 발행 요청 */
data class IssueCouponRequest(
    /** 쿠폰을 발행할 상점 ID (필수) */
    @field:NotNull
    val storeId: UUID?,
    /** 쿠폰을 받을 유저 ID (필수) */
    @field:NotNull
    val userId: UUID?,
)

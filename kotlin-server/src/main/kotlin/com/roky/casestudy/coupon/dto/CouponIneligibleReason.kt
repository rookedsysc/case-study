package com.roky.casestudy.coupon.dto

/** 쿠폰 발급 불가 사유 */
enum class CouponIneligibleReason {
    SOLD_OUT,
    ALREADY_ISSUED,
    IN_PROGRESS,
}

package com.roky.casestudy.coupon

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CouponUserLock(
    val userIdExpression: String,
)

package com.roky.casestudy.coupon.exception

import java.util.UUID

/** 동일 사용자의 쿠폰 발급 요청이 이미 진행 중인 경우 발생합니다. */
class CouponIssueInProgressException(
    userId: UUID,
) : RuntimeException("유저 $userId 의 쿠폰 발급 요청이 이미 처리 중입니다")

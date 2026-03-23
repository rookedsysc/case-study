package com.roky.casestudy.coupon.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

/** Kafka 쿠폰 발행 이벤트 페이로드 */
data class CouponIssueEvent
    @JsonCreator
    constructor(
        @param:JsonProperty("couponId")
        val couponId: UUID,
        @param:JsonProperty("storeId")
        val storeId: UUID,
        @param:JsonProperty("userId")
        val userId: UUID,
        @param:JsonProperty("issuedAt")
        val issuedAt: Instant,
)

package com.roky.casestudy.store.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

/** 상점 생성 요청 */
data class CreateStoreRequest(
    /** 상점 이름 (필수) */
    @field:NotBlank
    val name: String,
    /** 이벤트 전체 쿠폰 발행 수량 (1 이상) */
    @field:Min(1)
    val eventTotalCount: Long,
)

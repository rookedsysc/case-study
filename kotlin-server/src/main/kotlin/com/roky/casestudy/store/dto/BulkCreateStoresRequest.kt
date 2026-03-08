package com.roky.casestudy.store.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/** 상점 대량 생성 요청 */
data class BulkCreateStoresRequest(
    /** 생성할 상점 수 (1 이상 1000 이하) */
    @field:Min(1)
    @field:Max(1000)
    val count: Int,
)

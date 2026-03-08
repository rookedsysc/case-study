package com.roky.casestudy.user.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/** 유저 대량 생성 요청 */
data class BulkCreateAppUsersRequest(
    /** 생성할 유저 수 (1 이상 1000 이하) */
    @field:Min(1)
    @field:Max(1000)
    val count: Int,
)

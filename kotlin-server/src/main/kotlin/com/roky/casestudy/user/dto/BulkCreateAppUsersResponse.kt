package com.roky.casestudy.user.dto

import java.util.UUID

/** 유저 대량 생성 응답 */
data class BulkCreateAppUsersResponse(
    /** 생성된 유저 ID 목록 */
    val ids: List<UUID>,
)

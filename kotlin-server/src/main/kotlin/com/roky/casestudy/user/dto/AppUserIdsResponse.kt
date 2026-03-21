package com.roky.casestudy.user.dto

import java.util.UUID

/** 기존 유저 ID 목록 응답 */
data class AppUserIdsResponse(
    /** 조회된 유저 ID 목록 */
    val ids: List<UUID>,
)

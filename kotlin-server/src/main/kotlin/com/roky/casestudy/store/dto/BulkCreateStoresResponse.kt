package com.roky.casestudy.store.dto

import java.util.UUID

/** 상점 대량 생성 응답 */
data class BulkCreateStoresResponse(
    /** 생성된 상점 ID 목록 */
    val ids: List<UUID>,
)

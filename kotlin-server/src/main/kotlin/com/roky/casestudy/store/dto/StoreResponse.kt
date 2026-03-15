package com.roky.casestudy.store.dto

import java.time.Instant
import java.util.UUID

/** 상점 응답 */
data class StoreResponse(
    val id: UUID,
    val name: String,
    val eventTotalCount: Long,
    val createdAt: Instant,
)

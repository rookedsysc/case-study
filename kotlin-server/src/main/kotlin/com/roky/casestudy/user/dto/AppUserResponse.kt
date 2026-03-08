package com.roky.casestudy.user.dto

import java.time.Instant
import java.util.UUID

/** 유저 응답 */
data class AppUserResponse(
    val id: UUID,
    val name: String,
    val createdAt: Instant,
)

package com.roky.casestudy.user.dto

import jakarta.validation.constraints.NotBlank

/** 유저 생성 요청 */
data class CreateAppUserRequest(
    /** 유저 이름 (필수) */
    @field:NotBlank
    val name: String,
)

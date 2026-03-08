package com.roky.casestudy.store.dto

import jakarta.validation.constraints.NotBlank

/** 상점 생성 요청 */
data class CreateStoreRequest(
    /** 상점 이름 (필수) */
    @field:NotBlank
    val name: String,
)

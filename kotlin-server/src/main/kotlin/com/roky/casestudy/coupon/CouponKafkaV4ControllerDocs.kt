package com.roky.casestudy.coupon

import com.roky.casestudy.common.ErrorResponse
import com.roky.casestudy.coupon.dto.CouponResponse
import com.roky.casestudy.coupon.dto.IssueCouponRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "shopping mall case", description = "쇼핑몰 케이스 스터디 API")
interface CouponKafkaV4ControllerDocs {
    @Operation(summary = "쿠폰 발행 v4", description = "Redis 기반 검증 후 쿠폰 발급 이벤트를 Kafka로 발행하고 발급 메트릭을 측정합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "발행 성공",
                content = [Content(schema = Schema(implementation = CouponResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "입력값 검증 실패 또는 필수값 누락",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "상점 또는 유저를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "발급 처리 중이거나 이미 발행된 쿠폰 요청",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "410",
                description = "쿠폰 수량 소진",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "500",
                description = "서버 내부 오류",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun issueCoupon(
        @Parameter(description = "쿠폰 발행 요청", required = true)
        request: IssueCouponRequest,
    ): CouponResponse
}

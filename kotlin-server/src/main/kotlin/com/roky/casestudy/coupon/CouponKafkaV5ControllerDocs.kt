package com.roky.casestudy.coupon

import com.roky.casestudy.common.ErrorResponse
import com.roky.casestudy.coupon.dto.CouponIssueEligibilityResponse
import com.roky.casestudy.coupon.dto.CouponResponse
import com.roky.casestudy.coupon.dto.IssueCouponRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import java.util.UUID

@Tag(name = "shopping mall case", description = "쇼핑몰 케이스 스터디 API")
interface CouponKafkaV5ControllerDocs {
    @Operation(
        summary = "쿠폰 발급 가능 여부 조회",
        description = "솔드아웃 로컬캐시와 발급 유저 Redis Set을 확인하여 발급 가능 여부를 반환합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(schema = Schema(implementation = CouponIssueEligibilityResponse::class))],
            ),
        ],
    )
    fun checkEligibility(
        @Parameter(description = "상점 ID", required = true) storeId: UUID,
        @Parameter(description = "유저 ID", required = true) userId: UUID,
    ): CouponIssueEligibilityResponse

    @Operation(
        summary = "쿠폰 발행 v5 (BFF)",
        description = "솔드아웃·따닥 방지만 검증하고 Kafka 이벤트를 발행합니다. 재고 차감과 중복 처리는 Consumer가 수행합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "202",
                description = "발행 요청 접수",
                content = [Content(schema = Schema(implementation = CouponResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "입력값 검증 실패 또는 필수값 누락",
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

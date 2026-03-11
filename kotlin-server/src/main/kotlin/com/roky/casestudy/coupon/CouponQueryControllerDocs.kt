package com.roky.casestudy.coupon

import com.roky.casestudy.common.ErrorResponse
import com.roky.casestudy.coupon.dto.CouponStoreStatisticsResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import java.util.UUID

@Tag(name = "shopping mall case", description = "쇼핑몰 케이스 스터디 API")
interface CouponQueryControllerDocs {
    @Operation(summary = "상점 쿠폰 통계 조회", description = "상점 기준 쿠폰 발급 통계를 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(schema = Schema(implementation = CouponStoreStatisticsResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "상점을 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun getStoreCouponStatistics(
        @Parameter(
            description = "상점 ID",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000",
        )
        storeId: UUID,
    ): CouponStoreStatisticsResponse
}

package com.roky.casestudy.user

import com.roky.casestudy.common.ErrorResponse
import com.roky.casestudy.user.dto.AppUserResponse
import com.roky.casestudy.user.dto.BulkCreateAppUsersRequest
import com.roky.casestudy.user.dto.BulkCreateAppUsersResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import java.util.UUID

@Tag(name = "shopping mall case", description = "쇼핑몰 케이스 스터디 API")
interface AppUserControllerDocs {
    @Operation(summary = "유저 조회", description = "유저 ID로 유저 정보를 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(schema = Schema(implementation = AppUserResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "유저를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun getUser(
        @Parameter(
            description = "유저 ID",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000",
        )
        id: UUID,
    ): AppUserResponse

    @Operation(summary = "유저 일괄 생성", description = "부하 테스트용 유저 데이터를 최대 1000개까지 일괄 생성합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "생성 성공",
                content = [Content(schema = Schema(implementation = BulkCreateAppUsersResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "입력값 검증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun createUsersBulk(
        @Parameter(description = "유저 일괄 생성 요청", required = true)
        request: BulkCreateAppUsersRequest,
    ): BulkCreateAppUsersResponse
}

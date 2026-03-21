package com.roky.casestudy.user

import com.roky.casestudy.common.ErrorResponse
import com.roky.casestudy.user.dto.AppUserIdsResponse
import com.roky.casestudy.user.dto.BulkCreateAppUsersRequest
import com.roky.casestudy.user.dto.BulkCreateAppUsersResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

@Tag(name = "shopping mall case", description = "쇼핑몰 케이스 스터디 API")
interface AppUserControllerDocs {
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

    @Operation(summary = "기존 유저 ID 목록 조회", description = "부하 테스트에서 재사용할 기존 유저 ID를 페이지 단위로 최대 2000개까지 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(schema = Schema(implementation = AppUserIdsResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "입력값 검증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun getUserIds(
        @Parameter(description = "조회할 페이지 번호", required = false)
        @Min(0)
        page: Int,
        @Parameter(description = "페이지당 조회 개수 (1 이상 2000 이하)", required = false)
        @Min(1)
        @Max(2000)
        size: Int,
    ): AppUserIdsResponse
}

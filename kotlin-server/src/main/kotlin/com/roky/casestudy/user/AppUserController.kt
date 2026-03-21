package com.roky.casestudy.user

import com.roky.casestudy.user.dto.AppUserIdsResponse
import com.roky.casestudy.user.dto.BulkCreateAppUsersRequest
import com.roky.casestudy.user.dto.BulkCreateAppUsersResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/users")
class AppUserController(
    private val appUserService: AppUserService,
) : AppUserControllerDocs {
    /** 유저를 최대 1000개까지 일괄 생성합니다. 부하 테스트 사전 데이터 생성 용도입니다. */
    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    override fun createUsersBulk(
        @Valid @RequestBody request: BulkCreateAppUsersRequest,
    ): BulkCreateAppUsersResponse = appUserService.createUsersBulk(request.count)

    /** 기존 유저 ID를 페이지 단위로 최대 2000개까지 조회합니다. 부하 테스트 사전 데이터 조회 용도입니다. */
    @GetMapping("/ids")
    override fun getUserIds(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "2000") @Min(1) @Max(2000) size: Int,
    ): AppUserIdsResponse = appUserService.getUserIds(page, size)
}

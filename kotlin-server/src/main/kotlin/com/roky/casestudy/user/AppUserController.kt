package com.roky.casestudy.user

import com.roky.casestudy.user.dto.BulkCreateAppUsersRequest
import com.roky.casestudy.user.dto.BulkCreateAppUsersResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

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
}

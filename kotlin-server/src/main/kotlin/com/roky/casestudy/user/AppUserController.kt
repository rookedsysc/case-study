package com.roky.casestudy.user

import com.roky.casestudy.user.dto.AppUserResponse
import com.roky.casestudy.user.dto.BulkCreateAppUsersRequest
import com.roky.casestudy.user.dto.BulkCreateAppUsersResponse
import com.roky.casestudy.user.dto.CreateAppUserRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/users")
class AppUserController(private val appUserService: AppUserService) {

    /** 유저를 생성합니다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(@Valid @RequestBody request: CreateAppUserRequest): AppUserResponse =
        appUserService.createUser(request)

    /** 유저를 ID로 조회합니다. */
    @GetMapping("/{id}")
    fun getUser(@PathVariable id: UUID): AppUserResponse =
        appUserService.getUser(id)

    /** 유저를 최대 1000개까지 일괄 생성합니다. 부하 테스트 사전 데이터 생성 용도입니다. */
    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    fun createUsersBulk(@Valid @RequestBody request: BulkCreateAppUsersRequest): BulkCreateAppUsersResponse =
        appUserService.createUsersBulk(request.count)
}

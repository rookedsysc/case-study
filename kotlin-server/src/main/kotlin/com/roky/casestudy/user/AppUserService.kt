package com.roky.casestudy.user

import com.roky.casestudy.user.dto.AppUserResponse
import com.roky.casestudy.user.dto.BulkCreateAppUsersResponse
import com.roky.casestudy.user.dto.CreateAppUserRequest
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AppUserService(
    private val appUserRepository: AppUserRepository,
) {
    /**
     * 유저를 ID로 조회합니다.
     *
     * @throws NoSuchElementException 유저가 존재하지 않는 경우
     */
    fun getUser(id: UUID): AppUserResponse {
        val user =
            appUserRepository
                .findById(id)
                .orElseThrow { NoSuchElementException("유저를 찾을 수 없습니다: $id") }
        return AppUserMapper.toResponse(user)
    }

    /**
     * 유저를 count개 일괄 생성하고 생성된 ID 목록을 반환합니다.
     * 부하 테스트 사전 데이터 생성 용도로 사용됩니다.
     *
     * @param count 생성할 유저 수 (1 이상 1000 이하)
     * @returns 생성된 유저 ID 목록
     */
    fun createUsersBulk(count: Int): BulkCreateAppUsersResponse {
        val users = (1..count).map { AppUserEntity(name = "bulk_user_${UUID.randomUUID()}") }
        val savedUsers = appUserRepository.saveAll(users)
        return BulkCreateAppUsersResponse(ids = savedUsers.map { it.id })
    }
}

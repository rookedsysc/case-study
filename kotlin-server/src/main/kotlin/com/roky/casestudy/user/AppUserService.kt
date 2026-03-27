package com.roky.casestudy.user

import com.roky.casestudy.user.dto.AppUserIdsResponse
import com.roky.casestudy.user.dto.BulkCreateAppUsersResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AppUserService(
    private val appUserRepository: AppUserRepository,
    private val appUserRedisSetStore: AppUserRedisSetStore,
) {
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
        val savedUserIds = savedUsers.map { it.id }
        appUserRedisSetStore.addUsers(savedUserIds)
        return BulkCreateAppUsersResponse(ids = savedUserIds)
    }

    /**
     * 기존 유저 ID를 페이지 단위로 조회합니다.
     * 부하 테스트에서 재사용할 유저 풀을 준비할 때 사용됩니다.
     *
     * @param page 조회할 페이지 번호 (0 이상)
     * @param size 페이지당 조회할 유저 수 (1 이상 2000 이하)
     * @returns 조회된 유저 ID 목록
     */
    fun getUserIds(page: Int, size: Int): AppUserIdsResponse {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"))
        val userIds = appUserRepository.findAll(pageable).content.map { it.id }
        appUserRedisSetStore.addUsers(userIds)
        return AppUserIdsResponse(ids = userIds)
    }
}

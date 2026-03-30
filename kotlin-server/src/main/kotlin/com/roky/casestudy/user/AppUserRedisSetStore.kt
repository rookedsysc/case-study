package com.roky.casestudy.user

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class AppUserRedisSetStore(
    private val redisTemplate: StringRedisTemplate,
) {
    fun addUsers(userIds: Collection<UUID>) {
        if (userIds.isEmpty()) {
            return
        }

        redisTemplate.opsForSet().add(userIdsKey(), *userIds.map(UUID::toString).toTypedArray())
    }

    private fun userIdsKey(): String = USER_IDS_KEY

    companion object {
        private const val USER_IDS_KEY = "app-user:v1:ids"
    }
}

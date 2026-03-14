package com.roky.casestudy.coupon

import com.roky.casestudy.store.StoreEntity
import com.roky.casestudy.user.AppUserEntity
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom
import java.util.UUID

@Component
class CouponIssueCacheAsideStore(
    private val redisTemplate: StringRedisTemplate,
    private val couponRedisCoordinator: CouponRedisCoordinator,
) {
    fun getStoreSnapshotWithShortLock(
        storeId: UUID,
        loader: () -> StoreEntity,
    ): CachedStoreSnapshot {
        return couponRedisCoordinator.loadWithShortLock(
            lockKey = couponRedisCoordinator.storeSnapshotLoadLockKey(storeId),
            readCached = { readStoreSnapshot(storeId) },
            loadAndCache = {
                val store = loader()
                val snapshot = CachedStoreSnapshot(storeId = store.id, eventTotalCount = store.eventTotalCount)
                redisTemplate.opsForValue()
                    .set(storeCacheKey(storeId), snapshot.eventTotalCount.toString(), cacheTtlWithJitter())
                snapshot
            },
        )
    }

    fun getUserId(
        userId: UUID,
        loader: () -> AppUserEntity,
    ): UUID {
        val cachedUser = redisTemplate.opsForValue().get(userCacheKey(userId))
        if (cachedUser != null) {
            return userId
        }

        val user = loader()
        redisTemplate.opsForValue().set(userCacheKey(userId), PRESENT_CACHE_VALUE, cacheTtlWithJitter())
        return user.id
    }

    fun hasIssuedCoupon(
        storeId: UUID,
        userId: UUID,
        loader: () -> Boolean,
    ): Boolean {
        val cachedIssued = redisTemplate.opsForValue().get(couponIssuedCacheKey(storeId, userId))
        if (cachedIssued != null) {
            return cachedIssued == ISSUED_CACHE_VALUE
        }

        val issued = loader()
        redisTemplate.opsForValue().set(
            couponIssuedCacheKey(storeId, userId),
            if (issued) ISSUED_CACHE_VALUE else NOT_ISSUED_CACHE_VALUE,
            cacheTtlWithJitter(),
        )
        return issued
    }

    fun markCouponIssued(
        storeId: UUID,
        userId: UUID,
    ) {
        redisTemplate.opsForValue().set(couponIssuedCacheKey(storeId, userId), ISSUED_CACHE_VALUE, cacheTtlWithJitter())
    }

    private fun cacheTtlWithJitter(): Duration = Duration.ofSeconds(ThreadLocalRandom.current().nextLong(60, 131))

    private fun readStoreSnapshot(storeId: UUID): CachedStoreSnapshot? {
        val cachedEventTotalCount = redisTemplate.opsForValue().get(storeCacheKey(storeId)) ?: return null
        return CachedStoreSnapshot(storeId = storeId, eventTotalCount = cachedEventTotalCount.toLong())
    }

    private fun storeCacheKey(storeId: UUID): String = "coupon:v2:store:event-total-count:$storeId"

    private fun userCacheKey(userId: UUID): String = "coupon:v2:user:$userId"

    private fun couponIssuedCacheKey(
        storeId: UUID,
        userId: UUID,
    ): String = "coupon:v2:issued:$storeId:$userId"

    companion object {
        private const val PRESENT_CACHE_VALUE = "present"
        private const val ISSUED_CACHE_VALUE = "issued"
        private const val NOT_ISSUED_CACHE_VALUE = "not-issued"
    }
}

data class CachedStoreSnapshot(
    val storeId: UUID,
    val eventTotalCount: Long,
)

package com.roky.casestudy.coupon

import com.roky.casestudy.store.StoreEntity
import com.roky.casestudy.user.AppUserEntity
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

@Component
class CouponIssueCacheAsideStore(
    private val redisTemplate: StringRedisTemplate,
    private val couponRedisCoordinator: CouponRedisCoordinator,
) {
    fun getStoreSnapshotWithShortLock(
        storeId: UUID,
        loader: () -> StoreEntity,
    ): CachedStoreSnapshot =
        couponRedisCoordinator.loadWithShortLock(
            lockKey = couponRedisCoordinator.storeSnapshotLoadLockKey(storeId),
            readCached = { readStoreSnapshot(storeId) },
            loadAndCache = {
                val store = loader()
                val snapshot = CachedStoreSnapshot(storeId = store.id, eventTotalCount = store.eventTotalCount)
                redisTemplate
                    .opsForValue()
                    .set(storeCacheKey(storeId), snapshot.eventTotalCount.toString(), cacheTtlWithJitter())
                snapshot
            },
        )

    fun verifyUserExistsWithCache(
        userId: UUID,
        loader: () -> AppUserEntity,
    ) {
        couponRedisCoordinator.loadWithShortLock(
            lockKey = couponRedisCoordinator.userLoadLockKey(userId),
            readCached = { redisTemplate.opsForValue().get(userCacheKey(userId))?.let { userId } },
            loadAndCache = {
                val user = loader()
                redisTemplate.opsForValue().set(userCacheKey(userId), PRESENT_CACHE_VALUE, cacheTtlWithJitter())
                user.id
            },
        )
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

    /** 유저 존재 여부와 쿠폰 중복 발급 여부를 단일 읽기 트랜잭션으로 검증합니다. */
    @Transactional(readOnly = true)
    fun verifyUserAndCheckDuplicateCoupon(
        storeId: UUID,
        userId: UUID,
        userLoader: () -> AppUserEntity,
        issuedLoader: () -> Boolean,
    ): Boolean {
        verifyUserExistsWithCache(userId, userLoader)
        return hasIssuedCoupon(storeId, userId, issuedLoader)
    }

    /** 유저 존재 여부를 검증한 뒤 발급 캐시를 원자적으로 선점합니다. 이미 발급된 경우 false를 반환합니다. */
    @Transactional(readOnly = true)
    fun verifyUserAndReserveCouponIssue(
        storeId: UUID,
        userId: UUID,
        userLoader: () -> AppUserEntity,
        issuedLoader: () -> Boolean,
    ): Boolean {
        verifyUserExistsWithCache(userId, userLoader)
        return couponRedisCoordinator.loadWithShortLock(
            lockKey = couponRedisCoordinator.couponIssueReservationLockKey(storeId, userId),
            readCached = {
                when (redisTemplate.opsForValue().get(couponIssuedCacheKey(storeId, userId))) {
                    ISSUED_CACHE_VALUE -> false
                    else -> null
                }
            },
            loadAndCache = {
                val hasIssuedCoupon = issuedLoader()
                redisTemplate.opsForValue().set(couponIssuedCacheKey(storeId, userId), ISSUED_CACHE_VALUE, cacheTtlWithJitter())
                !hasIssuedCoupon
            },
        )
    }

    fun markCouponIssued(
        storeId: UUID,
        userId: UUID,
    ) {
        redisTemplate.opsForValue().set(couponIssuedCacheKey(storeId, userId), ISSUED_CACHE_VALUE, cacheTtlWithJitter())
    }

    fun unmarkCouponIssued(
        storeId: UUID,
        userId: UUID,
    ) {
        redisTemplate.delete(couponIssuedCacheKey(storeId, userId))
    }

    private fun cacheTtlWithJitter(): Duration = Duration.ofSeconds(ThreadLocalRandom.current().nextLong(600, 661))

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

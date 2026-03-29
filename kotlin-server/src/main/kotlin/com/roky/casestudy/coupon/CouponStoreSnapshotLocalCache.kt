package com.roky.casestudy.coupon

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

@Component
class CouponStoreSnapshotLocalCache {
    private val storeSnapshotCache =
        Caffeine
            .newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterWrite(SNAPSHOT_CACHE_TTL)
            .build<UUID, CachedStoreSnapshot>()

    fun get(storeId: UUID): CachedStoreSnapshot? = storeSnapshotCache.getIfPresent(storeId)

    fun put(snapshot: CachedStoreSnapshot) {
        storeSnapshotCache.put(snapshot.storeId, snapshot)
    }

    companion object {
        private const val MAX_CACHE_SIZE = 10_000L
        private val SNAPSHOT_CACHE_TTL: Duration = Duration.ofMinutes(60)
    }
}

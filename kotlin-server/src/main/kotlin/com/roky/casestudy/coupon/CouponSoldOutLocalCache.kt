package com.roky.casestudy.coupon

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

@Component
class CouponSoldOutLocalCache {
    private val soldOutStoreCache =
        Caffeine
            .newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterWrite(SOLD_OUT_CACHE_TTL)
            .build<UUID, Boolean>()

    fun isSoldOut(storeId: UUID): Boolean = soldOutStoreCache.getIfPresent(storeId) == true

    fun markSoldOut(storeId: UUID) {
        soldOutStoreCache.put(storeId, true)
    }

    fun evict(storeId: UUID) {
        soldOutStoreCache.invalidate(storeId)
    }

    companion object {
        private const val MAX_CACHE_SIZE = 10_000L
        private val SOLD_OUT_CACHE_TTL: Duration = Duration.ofMinutes(60)
    }
}

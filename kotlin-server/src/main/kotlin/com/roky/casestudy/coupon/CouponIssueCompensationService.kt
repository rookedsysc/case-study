package com.roky.casestudy.coupon

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CouponIssueCompensationService(
    private val couponRedisCoordinator: CouponRedisCoordinator,
    private val couponSoldOutLocalCache: CouponSoldOutLocalCache,
    private val couponIssueCacheAsideStore: CouponIssueCacheAsideStore,
) {
    fun rollbackIssueAttempt(
        storeId: UUID,
        userId: UUID,
        isReserved: Boolean,
        isStockDecreased: Boolean,
    ) {
        if (isReserved) {
            couponIssueCacheAsideStore.unmarkCouponIssued(storeId, userId)
        }
        if (isStockDecreased) {
            couponSoldOutLocalCache.evict(storeId)
            couponRedisCoordinator.rollbackStock(storeId)
        }
    }

    fun rollbackReservedIssue(
        storeId: UUID,
        userId: UUID,
    ) {
        couponIssueCacheAsideStore.unmarkCouponIssued(storeId, userId)
        couponSoldOutLocalCache.evict(storeId)
        couponRedisCoordinator.rollbackStock(storeId)
    }
}

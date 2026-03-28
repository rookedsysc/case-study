package com.roky.casestudy.coupon

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CouponIssueCompensationService(
    private val couponRedisCoordinator: CouponRedisCoordinator,
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
            couponRedisCoordinator.rollbackStock(storeId)
        }
    }

    fun rollbackReservedIssue(
        storeId: UUID,
        userId: UUID,
    ) {
        couponIssueCacheAsideStore.unmarkCouponIssued(storeId, userId)
        couponRedisCoordinator.rollbackStock(storeId)
    }
}

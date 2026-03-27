package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponResponse
import com.roky.casestudy.coupon.dto.IssueCouponRequest
import com.roky.casestudy.coupon.exception.CouponLimitExceededException
import com.roky.casestudy.coupon.exception.DuplicateCouponException
import com.roky.casestudy.store.StoreRepository
import org.springframework.stereotype.Service

@Service
class CouponRedisLockV2Service(
    private val couponRepository: CouponRepository,
    private val couponCommandService: CouponCommandService,
    private val couponRedisCoordinator: CouponRedisCoordinator,
    private val couponIssueCacheAsideStore: CouponIssueCacheAsideStore,
    private val storeRepository: StoreRepository,
) {
    /** Redis 락과 cache-aside 기반으로 쿠폰을 발행합니다. */
    fun issueCoupon(request: IssueCouponRequest): CouponResponse {
        val storeId = requireNotNull(request.storeId) { "storeId는 필수입니다" }
        val userId = requireNotNull(request.userId) { "userId는 필수입니다" }
        var shouldRollbackStock = false

        try {
            val store =
                couponIssueCacheAsideStore.getStoreSnapshotWithShortLock(storeId) {
                    storeRepository
                        .findById(storeId)
                        .orElseThrow { NoSuchElementException("상점을 찾을 수 없습니다: $storeId") }
                }

            shouldRollbackStock =
                couponRedisCoordinator.decreaseRemainingCoupon(
                    storeId = store.storeId,
                    eventTotalCount = store.eventTotalCount,
                    issuedCountLoader = { couponRepository.countByStoreId(storeId) },
                )
            if (!shouldRollbackStock) {
                throw CouponLimitExceededException(storeId)
            }

            val isDuplicate =
                couponIssueCacheAsideStore.reserveCouponIssue(
                    storeId = storeId,
                    userId = userId,
                )
            if (isDuplicate) {
                throw DuplicateCouponException(storeId, userId)
            }

            val coupon = couponCommandService.saveAndMarkIssued(storeId, userId)
            val response = CouponMapper.toResponse(coupon)
            return response
        }  catch (e: RuntimeException) {
            if (shouldRollbackStock) {
                couponRedisCoordinator.rollbackStock(storeId)
            }
            throw e
        }
    }
}

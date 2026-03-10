package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponResponse
import com.roky.casestudy.coupon.dto.IssueCouponRequest
import com.roky.casestudy.coupon.exception.CouponLimitExceededException
import com.roky.casestudy.coupon.exception.DuplicateCouponException
import com.roky.casestudy.store.StoreRepository
import com.roky.casestudy.user.AppUserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponRedisLockService(
    private val couponRepository: CouponRepository,
    private val couponRedisCoordinator: CouponRedisCoordinator,
    private val storeRepository: StoreRepository,
    private val appUserRepository: AppUserRepository,
) {
    /** Redis 락 기반으로 쿠폰을 발행합니다. */
    @CouponUserLock(userIdExpression = "#p0.userId")
    @Transactional
    fun issueCoupon(request: IssueCouponRequest): CouponResponse {
        val storeId = requireNotNull(request.storeId) { "storeId는 필수입니다" }
        val userId = requireNotNull(request.userId) { "userId는 필수입니다" }
        var stockDecreased = false

        try {
            val store = storeRepository.findById(storeId)
                .orElseThrow { NoSuchElementException("상점을 찾을 수 없습니다: $storeId") }

            val user = appUserRepository.findById(userId)
                .orElseThrow { NoSuchElementException("유저를 찾을 수 없습니다: $userId") }

            if (couponRepository.existsByStoreIdAndUserId(storeId, userId)) {
                throw DuplicateCouponException(storeId, userId)
            }

            stockDecreased = couponRedisCoordinator.decreaseRemainingStock(
                storeId = storeId,
                eventTotalCount = store.eventTotalCount,
                issuedCountLoader = { couponRepository.countByStoreId(storeId) },
            )
            if (!stockDecreased) {
                throw CouponLimitExceededException(storeId)
            }

            val coupon = CouponEntity(store = store, user = user)
            return CouponMapper.toResponse(couponRepository.save(coupon))
        } catch (e: RuntimeException) {
            if (stockDecreased) {
                couponRedisCoordinator.rollbackStock(storeId)
            }
            throw e
        }
    }
}

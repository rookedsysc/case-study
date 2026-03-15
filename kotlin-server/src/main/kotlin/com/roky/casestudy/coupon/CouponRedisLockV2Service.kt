package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponResponse
import com.roky.casestudy.coupon.dto.IssueCouponRequest
import com.roky.casestudy.coupon.exception.CouponLimitExceededException
import com.roky.casestudy.coupon.exception.DuplicateCouponException
import com.roky.casestudy.store.StoreRepository
import com.roky.casestudy.user.AppUserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

@Service
class CouponRedisLockV2Service(
    private val couponRepository: CouponRepository,
    private val couponRedisCoordinator: CouponRedisCoordinator,
    private val couponIssueCacheAsideStore: CouponIssueCacheAsideStore,
    private val storeRepository: StoreRepository,
    private val appUserRepository: AppUserRepository,
) {
    /** Redis 락과 cache-aside 기반으로 쿠폰을 발행합니다. */
    fun issueCoupon(request: IssueCouponRequest): CouponResponse {
        val storeId = requireNotNull(request.storeId) { "storeId는 필수입니다" }
        val userId = requireNotNull(request.userId) { "userId는 필수입니다" }
        var stockDecreased = false

        try {
            val store =
                couponIssueCacheAsideStore.getStoreSnapshotWithShortLock(storeId) {
                    storeRepository
                        .findById(storeId)
                        .orElseThrow { NoSuchElementException("상점을 찾을 수 없습니다: $storeId") }
                }

            stockDecreased =
                couponRedisCoordinator.decreaseRemainingStock(
                    storeId = store.storeId,
                    eventTotalCount = store.eventTotalCount,
                    issuedCountLoader = { couponRepository.countByStoreId(storeId) },
                )
            if (!stockDecreased) {
                throw CouponLimitExceededException(storeId)
            }

            val isDuplicate =
                couponIssueCacheAsideStore.verifyUserAndCheckDuplicateCoupon(
                    storeId = storeId,
                    userId = userId,
                    userLoader = {
                        appUserRepository
                            .findById(userId)
                            .orElseThrow { NoSuchElementException("유저를 찾을 수 없습니다: $userId") }
                    },
                    issuedLoader = { couponRepository.existsByStoreIdAndUserId(storeId, userId) },
                )
            if (isDuplicate) {
                throw DuplicateCouponException(storeId, userId)
            }

            val coupon =
                couponRepository.save(
                    CouponEntity(
                        store = storeRepository.getReferenceById(storeId),
                        user = appUserRepository.getReferenceById(userId),
                    ),
                )
            stockDecreased = false
            couponIssueCacheAsideStore.markCouponIssued(storeId, userId)
            return CouponMapper.toResponse(coupon)
        } catch (e: DataIntegrityViolationException) {
            if (stockDecreased) {
                couponRedisCoordinator.rollbackStock(storeId)
            }
            if (e.mostSpecificCause.message?.contains(COUPON_UNIQUE_CONSTRAINT_NAME) == true) {
                couponIssueCacheAsideStore.markCouponIssued(storeId, userId)
                throw DuplicateCouponException(storeId, userId)
            }
            throw e
        } catch (e: RuntimeException) {
            if (stockDecreased) {
                couponRedisCoordinator.rollbackStock(storeId)
            }
            throw e
        }
    }

    companion object {
        private const val COUPON_UNIQUE_CONSTRAINT_NAME = "uk_coupon_store_user"
    }
}

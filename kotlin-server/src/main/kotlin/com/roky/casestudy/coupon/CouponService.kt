package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponResponse
import com.roky.casestudy.coupon.dto.CouponStoreStatisticsResponse
import com.roky.casestudy.coupon.dto.IssueCouponRequest
import com.roky.casestudy.coupon.exception.CouponLimitExceededException
import com.roky.casestudy.coupon.exception.DuplicateCouponException
import com.roky.casestudy.store.StoreRepository
import com.roky.casestudy.user.AppUserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private const val STORE_COUPON_STATISTICS_TOP_USER_LIMIT = 10

@Service
class CouponService(
    private val couponRepository: CouponRepository,
    private val storeRepository: StoreRepository,
    private val appUserRepository: AppUserRepository,
) {
    /**
     * 쿠폰을 발행합니다. 상점별 eventTotalCount 범위 내에서만 발행되며, 유저는 상점당 1개만 받을 수 있습니다.
     * 동시 요청에 대한 정합성 보장을 위해 상점 레코드에 비관적 쓰기 락을 적용합니다.
     *
     * @param request 쿠폰 발행 요청 (storeId, userId)
     * @return 발행된 쿠폰 정보
     * @throws NoSuchElementException 상점 또는 유저가 존재하지 않는 경우
     * @throws DuplicateCouponException 이미 해당 상점의 쿠폰을 보유한 경우
     * @throws CouponLimitExceededException 상점 쿠폰 수량이 소진된 경우
     */
    @Transactional
    fun issueCoupon(request: IssueCouponRequest): CouponResponse {
        val storeId = requireNotNull(request.storeId) { "storeId는 필수입니다" }
        val userId = requireNotNull(request.userId) { "userId는 필수입니다" }

        val store =
            storeRepository
                .findByIdWithLock(storeId)
                .orElseThrow { NoSuchElementException("상점을 찾을 수 없습니다: $storeId") }

        val user =
            appUserRepository
                .findById(userId)
                .orElseThrow { NoSuchElementException("유저를 찾을 수 없습니다: $userId") }

        if (couponRepository.existsByStoreIdAndUserId(storeId, userId)) {
            throw DuplicateCouponException(storeId, userId)
        }

        val issuedCount = couponRepository.countByStoreId(storeId)
        if (issuedCount >= store.eventTotalCount) {
            throw CouponLimitExceededException(storeId)
        }

        val coupon = CouponEntity(store = store, userId = userId)
        return CouponMapper.toResponse(couponRepository.save(coupon))
    }

    /** 특정 상점의 쿠폰 발급 현황을 조회합니다. */
    @Transactional(readOnly = true)
    fun getStoreCouponStatistics(storeId: UUID): CouponStoreStatisticsResponse {
        val store =
            storeRepository
                .findById(storeId)
                .orElseThrow { NoSuchElementException("상점을 찾을 수 없습니다: $storeId") }

        val issuedCouponCount = couponRepository.countByStoreId(storeId)
        val topIssuedUsers =
            couponRepository.findTopIssuedUsersByStoreId(
                storeId = storeId,
                pageable = PageRequest.of(0, STORE_COUPON_STATISTICS_TOP_USER_LIMIT),
            )

        return CouponStoreStatisticsResponse(
            storeId = store.id,
            eventTotalCount = store.eventTotalCount,
            issuedCouponCount = issuedCouponCount,
            remainingCouponCount = (store.eventTotalCount - issuedCouponCount).coerceAtLeast(0),
            topIssuedUsers = topIssuedUsers,
        )
    }
}

package com.roky.casestudy.coupon

import com.roky.casestudy.store.StoreRepository
import com.roky.casestudy.user.AppUserRepository
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 쿠폰 저장과 캐시 갱신을 트랜잭션으로 묶어 원자성을 보장합니다. */
@Service
class CouponCommandService(
    private val couponRepository: CouponRepository,
    private val couponIssueCacheAsideStore: CouponIssueCacheAsideStore,
    private val storeRepository: StoreRepository,
    private val appUserRepository: AppUserRepository,
) {
    /**
     * 쿠폰을 저장하고 발급 캐시를 갱신합니다.
     * markCouponIssued 실패 시 트랜잭션 롤백으로 save도 취소됩니다.
     */
    @Transactional
    fun saveAndMarkIssued(storeId: UUID, userId: UUID): CouponEntity {
        val coupon =
            couponRepository.save(
                CouponEntity(
                    store = storeRepository.getReferenceById(storeId),
                    user = appUserRepository.getReferenceById(userId),
                ),
            )
        couponIssueCacheAsideStore.markCouponIssued(storeId, userId)
        return coupon
    }
}

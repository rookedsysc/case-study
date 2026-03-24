package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponIssueEvent
import com.roky.casestudy.store.StoreRepository
import com.roky.casestudy.user.AppUserRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class CouponKafkaV4StageService(
    private val couponRepository: CouponRepository,
    private val couponRedisCoordinator: CouponRedisCoordinator,
    private val couponIssueCacheAsideStore: CouponIssueCacheAsideStore,
    private val storeRepository: StoreRepository,
    private val appUserRepository: AppUserRepository,
    private val couponIssueKafkaProducer: CouponIssueKafkaProducer,
) {
    @CouponIssueStageMetric(STORE_SNAPSHOT_STAGE)
    fun loadStoreSnapshot(storeId: UUID): CachedStoreSnapshot =
        couponIssueCacheAsideStore.getStoreSnapshotWithShortLock(storeId) {
            storeRepository
                .findById(storeId)
                .orElseThrow { NoSuchElementException("상점을 찾을 수 없습니다: $storeId") }
        }

    @CouponIssueStageMetric(STOCK_DECREASE_STAGE)
    fun decreaseRemainingStock(
        storeId: UUID,
        store: CachedStoreSnapshot,
    ): Boolean =
        couponRedisCoordinator.decreaseRemainingStock(
            storeId = store.storeId,
            eventTotalCount = store.eventTotalCount,
            issuedCountLoader = { couponRepository.countByStoreId(storeId) },
        )

    @CouponIssueStageMetric(DUPLICATE_CHECK_STAGE)
    fun reserveCouponIssue(
        storeId: UUID,
        userId: UUID,
    ): Boolean =
        couponIssueCacheAsideStore.verifyUserAndReserveCouponIssue(
            storeId = storeId,
            userId = userId,
            userLoader = {
                appUserRepository
                    .findById(userId)
                    .orElseThrow { NoSuchElementException("유저를 찾을 수 없습니다: $userId") }
            },
            issuedLoader = { couponRepository.existsByStoreIdAndUserId(storeId, userId) },
        )

    @CouponIssueStageMetric(KAFKA_PUBLISH_STAGE)
    fun publishCouponIssue(
        storeId: UUID,
        userId: UUID,
        couponId: UUID,
        issuedAt: Instant,
    ) {
        couponIssueKafkaProducer.publishCouponIssue(
            CouponIssueEvent(
                couponId = couponId,
                storeId = storeId,
                userId = userId,
                issuedAt = issuedAt,
            ),
        )
    }

    fun unmarkCouponIssued(
        storeId: UUID,
        userId: UUID,
    ) {
        couponIssueCacheAsideStore.unmarkCouponIssued(storeId, userId)
    }
}

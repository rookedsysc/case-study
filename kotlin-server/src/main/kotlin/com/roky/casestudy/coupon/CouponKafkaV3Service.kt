package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponIssueEvent
import com.roky.casestudy.coupon.dto.CouponResponse
import com.roky.casestudy.coupon.dto.IssueCouponRequest
import com.roky.casestudy.coupon.exception.CouponLimitExceededException
import com.roky.casestudy.coupon.exception.DuplicateCouponException
import com.roky.casestudy.store.StoreRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/** Kafka 이벤트 스트리밍 기반 쿠폰 발행 서비스. DB write를 Kafka로 비동기 처리합니다. */
@Service
class CouponKafkaV3Service(
    private val couponRepository: CouponRepository,
    private val couponRedisCoordinator: CouponRedisCoordinator,
    private val couponIssueCacheAsideStore: CouponIssueCacheAsideStore,
    private val storeRepository: StoreRepository,
    private val couponIssueKafkaProducer: CouponIssueKafkaProducer,
) {
    /** 재고 감소와 중복 검증은 동기 처리하고, DB 저장만 Kafka로 비동기 발행합니다. */
    fun issueCoupon(request: IssueCouponRequest): CouponResponse {
        val storeId = requireNotNull(request.storeId) { "storeId는 필수입니다" }
        val userId = requireNotNull(request.userId) { "userId는 필수입니다" }
        var isStockDecreased = false
        var isReserved = false

        try {
            val store =
                couponIssueCacheAsideStore.getStoreSnapshotWithShortLock(storeId) {
                    storeRepository
                        .findById(storeId)
                        .orElseThrow { NoSuchElementException("상점을 찾을 수 없습니다: $storeId") }
                }

            isStockDecreased =
                couponRedisCoordinator.decreaseRemainingCoupon(
                    storeId = store.storeId,
                    eventTotalCount = store.eventTotalCount,
                    issuedCountLoader = { couponRepository.countByStoreId(storeId) },
                )
            if (!isStockDecreased) {
                throw CouponLimitExceededException(storeId)
            }

            isReserved =
                couponIssueCacheAsideStore.reserveCouponIssue(
                    storeId = storeId,
                    userId = userId,
                )
            if (!isReserved) {
                throw DuplicateCouponException(storeId, userId)
            }

            val couponId = UUID.randomUUID()
            val issuedAt = Instant.now()

            couponIssueKafkaProducer.publishCouponIssue(
                CouponIssueEvent(
                    couponId = couponId,
                    storeId = storeId,
                    userId = userId,
                    issuedAt = issuedAt,
                ),
            )
            return CouponResponse(
                id = couponId,
                storeId = storeId,
                userId = userId,
                issuedAt = issuedAt,
            )
        } catch (e: RuntimeException) {
            if (isReserved) {
                couponIssueCacheAsideStore.unmarkCouponIssued(storeId, userId)
            }
            if (isStockDecreased) {
                couponRedisCoordinator.rollbackStock(storeId)
            }
            throw e
        }
    }
}

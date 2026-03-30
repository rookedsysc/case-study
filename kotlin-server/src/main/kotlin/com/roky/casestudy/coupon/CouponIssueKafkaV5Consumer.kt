package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponIssueEvent
import com.roky.casestudy.coupon.exception.CouponLimitExceededException
import com.roky.casestudy.coupon.exception.DuplicateCouponException
import com.roky.casestudy.store.StoreRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * BFF 패턴 쿠폰 발급 Consumer. 재고 차감, 중복 처리, DB 저장을 모두 담당합니다.
 *
 * API 계층은 최소 검증만 수행하고 이벤트를 발행하므로,
 * 이 Consumer가 쿠폰 발급의 모든 상태 변경을 책임집니다.
 */
@Component
class CouponIssueKafkaV5Consumer(
    private val couponRepository: CouponRepository,
    private val storeRepository: StoreRepository,
    private val couponIssueCacheAsideStore: CouponIssueCacheAsideStore,
    private val couponRedisCoordinator: CouponRedisCoordinator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [CouponKafkaV5Service.TOPIC],
        groupId = "coupon-issue-v5-consumer",
    )
    fun consume(events: List<CouponIssueEvent>) {
        events.forEach { event ->
            try {
                processEvent(event)
            } catch (e: CouponLimitExceededException) {
                log.warn("재고 소진으로 발급 건너뜀: storeId={}, userId={}", event.storeId, event.userId)
            } catch (e: DuplicateCouponException) {
                log.warn("중복 발급 요청 건너뜀: storeId={}, userId={}", event.storeId, event.userId)
            }
        }
    }

    private fun processEvent(event: CouponIssueEvent) {
        val store =
            couponIssueCacheAsideStore.getStoreSnapshotWithShortLock(event.storeId) {
                storeRepository
                    .findById(event.storeId)
                    .orElseThrow { NoSuchElementException("상점을 찾을 수 없습니다: ${event.storeId}") }
            }

        val isStockDecreased =
            couponRedisCoordinator.decreaseRemainingCoupon(
                storeId = event.storeId,
                eventTotalCount = store.eventTotalCount,
                issuedCountLoader = { couponRepository.countByStoreId(event.storeId) },
            )

        if (!isStockDecreased) {
            throw CouponLimitExceededException(event.storeId)
        }

        val isReserved =
            couponIssueCacheAsideStore.reserveCouponIssue(
                storeId = event.storeId,
                userId = event.userId,
            )
        if (!isReserved) {
            couponRedisCoordinator.rollbackStock(event.storeId)
            throw DuplicateCouponException(event.storeId, event.userId)
        }

        try {
            couponRepository.saveAndFlush(toCouponEntity(event))
        } catch (_: DataIntegrityViolationException) {
            log.warn(
                "쿠폰 중복 저장 무시: couponId={}, storeId={}, userId={}",
                event.couponId,
                event.storeId,
                event.userId,
            )
        } catch (e: Exception) {
            log.error(
                "쿠폰 저장 실패: couponId={}, storeId={}, userId={}",
                event.couponId,
                event.storeId,
                event.userId,
                e,
            )
            runCatching { couponIssueCacheAsideStore.unmarkCouponIssued(event.storeId, event.userId) }
                .onFailure { log.error("쿠폰 발급 마킹 해제 실패: storeId={}, userId={}", event.storeId, event.userId, it) }
            runCatching { couponRedisCoordinator.rollbackStock(event.storeId) }
                .onFailure { log.error("재고 복구 실패: storeId={}", event.storeId, it) }
        }
    }

    private fun toCouponEntity(event: CouponIssueEvent): CouponEntity =
        CouponEntity(
            id = event.couponId,
            store = storeRepository.getReferenceById(event.storeId),
            userId = event.userId,
            issuedAt = event.issuedAt,
        )
}

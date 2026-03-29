package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponIssueEvent
import com.roky.casestudy.store.StoreRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Kafka 쿠폰 발행 이벤트를 소비하여 DB에 저장합니다.
 *
 * 스로틀링은 application.yml의 max.poll.records와 concurrency로 제어합니다.
 * 중복 저장 시 신규 발급이 아니므로 재고만 복구하고 issued 캐시는 유지합니다.
 */
@Component
class CouponIssueKafkaConsumer(
    private val couponRepository: CouponRepository,
    private val storeRepository: StoreRepository,
    private val couponIssueCacheAsideStore: CouponIssueCacheAsideStore,
    private val couponRedisCoordinator: CouponRedisCoordinator,
    private val couponSoldOutLocalCache: CouponSoldOutLocalCache,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [CouponIssueKafkaProducer.TOPIC], groupId = "coupon-issue-consumer")
    fun consume(events: List<CouponIssueEvent>) {
        if (events.isEmpty()) {
            return
        }

        try {
            couponRepository.saveAllAndFlush(events.map(::toCouponEntity))
        } catch (_: DataIntegrityViolationException) {
            events.forEach(::persistIndividually)
        } catch (e: Exception) {
            events.forEach { event ->
                handlePersistenceFailure(event, e)
            }
        }
    }

    private fun persistIndividually(event: CouponIssueEvent) {
        try {
            couponRepository.saveAndFlush(toCouponEntity(event))
        } catch (_: DataIntegrityViolationException) {
            log.warn("쿠폰 중복 저장 무시: storeId={}, userId={}", event.storeId, event.userId)
        } catch (e: Exception) {
            handlePersistenceFailure(event, e)
        }
    }

    private fun handlePersistenceFailure(
        event: CouponIssueEvent,
        exception: Exception,
    ) {
        log.error(
            "쿠폰 저장 실패: couponId={}, storeId={}, userId={}",
            event.couponId,
            event.storeId,
            event.userId,
            exception,
        )
        couponIssueCacheAsideStore.unmarkCouponIssued(event.storeId, event.userId)
        couponSoldOutLocalCache.evict(event.storeId)
        couponRedisCoordinator.rollbackStock(event.storeId)
    }

    private fun toCouponEntity(event: CouponIssueEvent): CouponEntity =
        CouponEntity(
            id = event.couponId,
            store = storeRepository.getReferenceById(event.storeId),
            userId = event.userId,
            issuedAt = event.issuedAt,
        )
}

package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponIssueEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

/**
 * V5 BFF 전용 비동기 Kafka Producer. 발행 결과를 블로킹하지 않고 콜백으로 처리합니다.
 * 재고 차감과 보상은 Consumer가 담당하므로 Producer 실패 시 로깅만 수행합니다.
 */
@Component
class CouponIssueRequestKafkaProducer(
    private val kafkaTemplate: KafkaTemplate<String, CouponIssueEvent>,
    @Value("\${coupon.kafka.issue.producer-shard-count:8}") private val producerShardCount: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 쿠폰 발급 요청 이벤트를 비동기로 발행합니다. 실패 시 로깅만 수행합니다. */
    fun publishAsync(event: CouponIssueEvent) {
        kafkaTemplate
            .send(CouponKafkaV5Service.TOPIC, resolvePartitionKey(event), event)
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    log.error(
                        "Kafka 쿠폰 발급 요청 발행 실패: couponId={}, storeId={}, userId={}",
                        event.couponId,
                        event.storeId,
                        event.userId,
                        throwable,
                    )
                }
            }
    }

    private fun resolvePartitionKey(event: CouponIssueEvent): String {
        val safeShardCount = producerShardCount.coerceAtLeast(1)
        val shard = (event.userId.hashCode() and Int.MAX_VALUE) % safeShardCount
        return "${event.storeId}:$shard"
    }
}

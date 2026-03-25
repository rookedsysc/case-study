package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponIssueEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/** Kafka로 쿠폰 발행 이벤트를 전송합니다. */
@Component
class CouponIssueKafkaProducer(
    private val kafkaTemplate: KafkaTemplate<String, CouponIssueEvent>,
    @Value("\${coupon.kafka.issue.producer-shard-count:8}") private val producerShardCount: Int,
) {
    /** store 단위 hot partition을 줄이기 위해 storeId에 userId 기반 shard를 더해 분산 전송합니다. */
    fun publishCouponIssue(event: CouponIssueEvent) {
        try {
            kafkaTemplate.send(TOPIC, resolvePartitionKey(event), event).get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw IllegalStateException("Kafka 쿠폰 발행에 실패했습니다", e.cause ?: e)
        }
    }

    private fun resolvePartitionKey(event: CouponIssueEvent): String {
        val safeShardCount = producerShardCount.coerceAtLeast(1)
        val shard = (event.userId.hashCode() and Int.MAX_VALUE) % safeShardCount
        return "${event.storeId}:$shard"
    }

    companion object {
        const val TOPIC = "coupon-issue"
        private const val PUBLISH_TIMEOUT_SECONDS = 10L
    }
}

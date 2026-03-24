package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponIssueEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/** Kafka로 쿠폰 발행 이벤트를 전송합니다. */
@Component
class CouponIssueKafkaProducer(
    private val kafkaTemplate: KafkaTemplate<String, CouponIssueEvent>,
) {
    /** storeId를 파티션 키로 사용하여 동일 상점의 이벤트가 같은 파티션으로 전송되도록 합니다. */
    fun publishCouponIssue(event: CouponIssueEvent) {
        try {
            kafkaTemplate.send(TOPIC, event.storeId.toString(), event).get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw IllegalStateException("Kafka 쿠폰 발행에 실패했습니다", e.cause ?: e)
        }
    }

    companion object {
        const val TOPIC = "coupon-issue"
        private const val PUBLISH_TIMEOUT_SECONDS = 10L
    }
}

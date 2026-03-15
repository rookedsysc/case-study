package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponIssueEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

/** Kafka로 쿠폰 발행 이벤트를 전송합니다. */
@Component
class CouponIssueKafkaProducer(
    private val kafkaTemplate: KafkaTemplate<String, CouponIssueEvent>,
) {
    /** storeId를 파티션 키로 사용하여 동일 상점의 이벤트가 같은 파티션으로 전송되도록 합니다. */
    fun publishCouponIssue(event: CouponIssueEvent) {
        kafkaTemplate.send(TOPIC, event.storeId.toString(), event)
    }

    companion object {
        const val TOPIC = "coupon-issue"
    }
}

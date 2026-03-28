package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponIssueEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/** Kafka로 쿠폰 발행 이벤트를 전송합니다. */
@Component
class CouponIssueKafkaProducer(
    private val kafkaTemplate: KafkaTemplate<String, CouponIssueEvent>,
    @Value("\${coupon.kafka.issue.producer-shard-count:8}") private val producerShardCount: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** store 단위 hot partition을 줄이기 위해 storeId에 userId 기반 shard를 더해 분산 전송합니다. */
    fun publishCouponIssue(
        event: CouponIssueEvent,
        onPublishFailure: (Throwable) -> Unit = {},
    ): CompletableFuture<Unit> =
        try {
            kafkaTemplate
                .send(TOPIC, resolvePartitionKey(event), event)
                .whenComplete { _, throwable ->
                    if (throwable == null) {
                        return@whenComplete
                    }

                    val cause = unwrapPublishException(throwable)
                    handlePublishFailure(event, cause, onPublishFailure)
                }.thenApply { Unit }
        } catch (e: Exception) {
            val cause = unwrapPublishException(e)
            handlePublishFailure(event, cause, onPublishFailure)
            CompletableFuture.failedFuture(IllegalStateException("Kafka 쿠폰 발행에 실패했습니다", cause))
        }

    private fun resolvePartitionKey(event: CouponIssueEvent): String {
        val safeShardCount = producerShardCount.coerceAtLeast(1)
        val shard = (event.userId.hashCode() and Int.MAX_VALUE) % safeShardCount
        return "${event.storeId}:$shard"
    }

    private fun handlePublishFailure(
        event: CouponIssueEvent,
        cause: Throwable,
        onPublishFailure: (Throwable) -> Unit,
    ) {
        log.error(
            "Kafka 쿠폰 발행 실패: couponId={}, storeId={}, userId={}",
            event.couponId,
            event.storeId,
            event.userId,
            cause,
        )
        runCatching { onPublishFailure(cause) }
            .onFailure { fallbackException ->
                log.error(
                    "Kafka 쿠폰 발행 fallback 실패: couponId={}, storeId={}, userId={}",
                    event.couponId,
                    event.storeId,
                    event.userId,
                    fallbackException,
                )
            }
    }

    private fun unwrapPublishException(throwable: Throwable): Throwable =
        when (throwable) {
            is CompletionException -> throwable.cause ?: throwable
            else -> throwable
        }

    companion object {
        const val TOPIC = "coupon-issue"
    }
}

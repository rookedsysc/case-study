package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponResponse
import com.roky.casestudy.coupon.dto.IssueCouponRequest
import com.roky.casestudy.coupon.exception.CouponLimitExceededException
import com.roky.casestudy.coupon.exception.DuplicateCouponException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/** Kafka 이벤트 스트리밍 기반 쿠폰 발행 서비스입니다. 발급 과정 메트릭을 함께 기록합니다. */
@Service
class CouponKafkaV4Service(
    private val couponIssueCompensationService: CouponIssueCompensationService,
    private val couponKafkaV4StageService: CouponKafkaV4StageService,
) {
    /** 재고 감소와 중복 검증은 동기 처리하고, DB 저장만 Kafka로 비동기 발행합니다. */
    @CouponIssueMetric
    fun issueCoupon(request: IssueCouponRequest): CouponResponse {
        val storeId = requireNotNull(request.storeId) { "storeId는 필수입니다" }
        val userId = requireNotNull(request.userId) { "userId는 필수입니다" }
        val couponId = UUID.randomUUID()
        val issuedAt = Instant.now()
        var isStockDecreased = false
        var isReserved = false

        try {
            val store = couponKafkaV4StageService.loadStoreSnapshot(storeId)

            isStockDecreased = couponKafkaV4StageService.decreaseRemainingCoupon(storeId, store)
            if (!isStockDecreased) {
                throw CouponLimitExceededException(storeId)
            }

            isReserved = couponKafkaV4StageService.reserveCouponIssue(storeId, userId)
            if (!isReserved) {
                throw DuplicateCouponException(storeId, userId)
            }
        } catch (e: RuntimeException) {
            couponIssueCompensationService.rollbackIssueAttempt(storeId, userId, isReserved, isStockDecreased)
            throw e
        }

        couponKafkaV4StageService.publishCouponIssue(
            storeId = storeId,
            userId = userId,
            couponId = couponId,
            issuedAt = issuedAt,
            onPublishFailure = { couponIssueCompensationService.rollbackReservedIssue(storeId, userId) },
        )

        return CouponResponse(
            id = couponId,
            storeId = storeId,
            userId = userId,
            issuedAt = issuedAt,
        )
    }
}

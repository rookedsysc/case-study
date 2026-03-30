package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponIneligibleReason
import com.roky.casestudy.coupon.dto.CouponIssueEligibilityResponse
import com.roky.casestudy.coupon.dto.CouponIssueEvent
import com.roky.casestudy.coupon.dto.CouponResponse
import com.roky.casestudy.coupon.dto.IssueCouponRequest
import com.roky.casestudy.coupon.exception.CouponIssueInProgressException
import com.roky.casestudy.coupon.exception.CouponLimitExceededException
import com.roky.casestudy.coupon.exception.DuplicateCouponException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * BFF 패턴 쿠폰 발급 서비스. API 계층에서는 최소한의 검증만 수행하고
 * 재고 차감과 중복 처리는 Consumer에 위임합니다.
 *
 * 솔드아웃은 Consumer의 decreaseRemainingCoupon이 마킹한 로컬캐시를 그대로 읽고,
 * 발급 여부는 Consumer가 markCouponIssued한 Redis Set을 SISMEMBER로 확인합니다.
 */
@Service
class CouponKafkaV5Service(
    private val couponSoldOutLocalCache: CouponSoldOutLocalCache,
    private val couponIssueCacheAsideStore: CouponIssueCacheAsideStore,
    private val couponIssueRequestKafkaProducer: CouponIssueRequestKafkaProducer,
    private val couponRedisCoordinator: CouponRedisCoordinator,
) {
    /**
     * 솔드아웃·중복·따닥 검증 후 쿠폰 발급 이벤트를 비동기로 Kafka에 발행합니다.
     * 재고 차감과 최종 중복 판정은 Consumer가 수행합니다.
     */
    fun issueCoupon(request: IssueCouponRequest): CouponResponse {
        val storeId = requireNotNull(request.storeId) { "storeId는 필수입니다" }
        val userId = requireNotNull(request.userId) { "userId는 필수입니다" }

        if (couponSoldOutLocalCache.isSoldOut(storeId)) {
            throw CouponLimitExceededException(storeId)
        }

        if (couponIssueCacheAsideStore.isCouponIssued(storeId, userId)) {
            throw DuplicateCouponException(storeId, userId)
        }

        if (!couponRedisCoordinator.tryAcquireDedup(storeId, userId)) {
            throw CouponIssueInProgressException(userId)
        }

        val couponId = UUID.randomUUID()
        val issuedAt = Instant.now()
        couponIssueRequestKafkaProducer.publishAsync(
            CouponIssueEvent(couponId = couponId, storeId = storeId, userId = userId, issuedAt = issuedAt),
        )

        return CouponResponse(id = couponId, storeId = storeId, userId = userId, issuedAt = issuedAt)
    }

    /**
     * 쿠폰 발급 가능 여부를 조회합니다.
     * Consumer와 동일한 솔드아웃 로컬캐시와 발급 유저 Redis Set을 읽어 판단합니다.
     */
    fun checkEligibility(
        storeId: UUID,
        userId: UUID,
    ): CouponIssueEligibilityResponse {
        if (couponSoldOutLocalCache.isSoldOut(storeId)) {
            return CouponIssueEligibilityResponse(
                storeId = storeId,
                userId = userId,
                eligible = false,
                reason = CouponIneligibleReason.SOLD_OUT,
            )
        }

        if (couponIssueCacheAsideStore.isCouponIssued(storeId, userId)) {
            return CouponIssueEligibilityResponse(
                storeId = storeId,
                userId = userId,
                eligible = false,
                reason = CouponIneligibleReason.ALREADY_ISSUED,
            )
        }

        if (couponRedisCoordinator.isDedupKeyPresent(storeId, userId)) {
            return CouponIssueEligibilityResponse(
                storeId = storeId,
                userId = userId,
                eligible = false,
                reason = CouponIneligibleReason.IN_PROGRESS,
            )
        }

        return CouponIssueEligibilityResponse(
            storeId = storeId,
            userId = userId,
            eligible = true,
        )
    }

    companion object {
        const val TOPIC = "coupon-issue-request"
    }
}

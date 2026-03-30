package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.exception.CouponLimitExceededException
import com.roky.casestudy.coupon.exception.DuplicateCouponException
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

@Aspect
@Component
class CouponIssueMetricsAspect(
    private val meterRegistry: MeterRegistry,
) {
    private val totalIssueTimer =
        Timer
            .builder(COUPON_ISSUE_TOTAL_TIMER)
            .description("쿠폰 발급 요청 전체 소요 시간")
            .publishPercentileHistogram()
            .register(meterRegistry)

    private val issueStageTimers =
        mapOf(
            STORE_SNAPSHOT_STAGE to registerStageTimer(STORE_SNAPSHOT_STAGE),
            STOCK_DECREASE_STAGE to registerStageTimer(STOCK_DECREASE_STAGE),
            DUPLICATE_CHECK_STAGE to registerStageTimer(DUPLICATE_CHECK_STAGE),
            KAFKA_PUBLISH_STAGE to registerStageTimer(KAFKA_PUBLISH_STAGE),
            CACHE_MARK_STAGE to registerStageTimer(CACHE_MARK_STAGE),
        )

    private val failureCounters =
        mapOf(
            LIMIT_EXCEEDED_REASON to registerFailureCounter(LIMIT_EXCEEDED_REASON),
            DUPLICATE_REASON to registerFailureCounter(DUPLICATE_REASON),
            UNEXPECTED_REASON to registerFailureCounter(UNEXPECTED_REASON),
        )

    @Around("@annotation(couponIssueMetric)")
    fun recordIssue(
        joinPoint: ProceedingJoinPoint,
        couponIssueMetric: CouponIssueMetric,
    ): Any? {
        val totalSample = Timer.start(meterRegistry)

        try {
            return joinPoint.proceed()
        } catch (e: CouponLimitExceededException) {
            countFailure(LIMIT_EXCEEDED_REASON)
            throw e
        } catch (e: DuplicateCouponException) {
            countFailure(DUPLICATE_REASON)
            throw e
        } catch (e: RuntimeException) {
            countFailure(UNEXPECTED_REASON)
            throw e
        } finally {
            totalSample.stop(totalIssueTimer)
        }
    }

    @Around("@annotation(couponIssueStageMetric)")
    fun recordStage(
        joinPoint: ProceedingJoinPoint,
        couponIssueStageMetric: CouponIssueStageMetric,
    ): Any? {
        val timer = issueStageTimers.getValue(couponIssueStageMetric.stage)

        return timer.recordCallable {
            joinPoint.proceed()
        }
    }

    private fun registerStageTimer(stage: String): Timer =
        Timer
            .builder(COUPON_ISSUE_STAGE_TIMER)
            .description("쿠폰 발급 단계별 소요 시간")
            .tag("stage", stage)
            .publishPercentileHistogram()
            .register(meterRegistry)

    private fun registerFailureCounter(reason: String): Counter =
        meterRegistry.counter(COUPON_ISSUE_FAILURE_COUNTER, "reason", reason)

    private fun countFailure(reason: String) {
        failureCounters.getValue(reason).increment()
    }
}

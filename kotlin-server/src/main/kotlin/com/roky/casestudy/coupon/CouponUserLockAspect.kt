package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.exception.CouponIssueInProgressException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Component
import java.util.UUID

@Aspect
@Component
class CouponUserLockAspect(
    private val couponRedisCoordinator: CouponRedisCoordinator,
) {
    private val parser = SpelExpressionParser()

    @Around("@annotation(couponUserLock)")
    fun withUserLock(
        joinPoint: ProceedingJoinPoint,
        couponUserLock: CouponUserLock,
    ): Any? {
        val userId = resolveUserId(joinPoint, couponUserLock.userIdExpression)
        return couponRedisCoordinator.withUserLock(userId) { joinPoint.proceed() }
    }

    private fun resolveUserId(
        joinPoint: ProceedingJoinPoint,
        expression: String,
    ): UUID {
        val context =
            StandardEvaluationContext().apply {
                joinPoint.args.forEachIndexed { index, arg ->
                    setVariable("p$index", arg)
                    setVariable("a$index", arg)
                }
            }

        val value =
            parser.parseExpression(expression).getValue(context)
                ?: throw IllegalArgumentException("userIdExpression 결과가 비어 있습니다")

        return when (value) {
            is UUID -> value
            is String -> UUID.fromString(value)
            else -> throw IllegalArgumentException("userIdExpression은 UUID 또는 UUID 문자열이어야 합니다")
        }
    }
}

package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponResponse
import com.roky.casestudy.coupon.dto.IssueCouponRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/coupons/redis-lock")
class CouponRedisLockV2Controller(
    private val couponRedisLockV2Service: CouponRedisLockV2Service,
) : CouponRedisLockV2ControllerDocs {
    /** Redis 락과 cache-aside 기반으로 쿠폰을 발행합니다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun issueCoupon(
        @Valid @RequestBody request: IssueCouponRequest,
    ): CouponResponse = couponRedisLockV2Service.issueCoupon(request)
}

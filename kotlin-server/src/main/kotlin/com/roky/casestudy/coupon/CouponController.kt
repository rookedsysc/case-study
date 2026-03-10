package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponResponse
import com.roky.casestudy.coupon.dto.IssueCouponRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/coupons/pessimistic-lock")
class CouponController(private val couponService: CouponService) {

    /** 쿠폰을 발행합니다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun issueCoupon(@Valid @RequestBody request: IssueCouponRequest): CouponResponse =
        couponService.issueCoupon(request)
}

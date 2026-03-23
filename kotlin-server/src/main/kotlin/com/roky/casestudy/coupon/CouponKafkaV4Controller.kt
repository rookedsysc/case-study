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
@RequestMapping("/api/v4/coupons/kafka")
class CouponKafkaV4Controller(
    private val couponKafkaV4Service: CouponKafkaV4Service,
) : CouponKafkaV4ControllerDocs {
    /** Redis 검증 후 쿠폰 발급 이벤트를 Kafka로 발행하고 메트릭을 기록합니다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun issueCoupon(
        @Valid @RequestBody request: IssueCouponRequest,
    ): CouponResponse = couponKafkaV4Service.issueCoupon(request)
}

package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponIssueEligibilityResponse
import com.roky.casestudy.coupon.dto.CouponResponse
import com.roky.casestudy.coupon.dto.IssueCouponRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v5/coupons/kafka")
class CouponKafkaV5Controller(
    private val couponKafkaV5Service: CouponKafkaV5Service,
) : CouponKafkaV5ControllerDocs {
    /** 쿠폰 발급 가능 여부를 조회합니다. 솔드아웃과 중복 발급 여부를 확인합니다. */
    @GetMapping("/eligibility")
    override fun checkEligibility(
        @RequestParam storeId: UUID,
        @RequestParam userId: UUID,
    ): CouponIssueEligibilityResponse = couponKafkaV5Service.checkEligibility(storeId, userId)

    /** 최소 검증 후 쿠폰 발급 이벤트를 Kafka로 발행합니다. 재고 차감은 Consumer가 수행합니다. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    override fun issueCoupon(
        @Valid @RequestBody request: IssueCouponRequest,
    ): CouponResponse = couponKafkaV5Service.issueCoupon(request)
}

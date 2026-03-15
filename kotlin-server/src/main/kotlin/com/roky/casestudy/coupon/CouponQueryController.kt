package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponStoreStatisticsResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/coupons/stores")
class CouponQueryController(
    private val couponService: CouponService,
) : CouponQueryControllerDocs {
    /** 상점 기준 쿠폰 발급 통계를 조회합니다. */
    @GetMapping("/{storeId}/statistics")
    override fun getStoreCouponStatistics(
        @PathVariable storeId: UUID,
    ): CouponStoreStatisticsResponse = couponService.getStoreCouponStatistics(storeId)
}

package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponResponse

object CouponMapper {
    fun toResponse(entity: CouponEntity): CouponResponse = CouponResponse(
        id = entity.id,
        storeId = entity.store.id,
        userId = entity.user.id,
        issuedAt = entity.issuedAt,
    )
}

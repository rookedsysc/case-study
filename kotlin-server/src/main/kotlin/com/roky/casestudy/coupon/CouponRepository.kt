package com.roky.casestudy.coupon

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CouponRepository : JpaRepository<CouponEntity, UUID> {

    /** 특정 상점에 발행된 쿠폰 수를 반환합니다. */
    fun countByStoreId(storeId: UUID): Long

    /** 유저가 특정 상점의 쿠폰을 이미 보유하는지 확인합니다. */
    fun existsByStoreIdAndUserId(storeId: UUID, userId: UUID): Boolean
}

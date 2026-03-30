package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.dto.CouponStoreTopUserResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface CouponRepository : JpaRepository<CouponEntity, UUID> {
    /** 특정 상점에 발행된 쿠폰 수를 반환합니다. */
    fun countByStoreId(storeId: UUID): Long

    /** 유저가 특정 상점의 쿠폰을 이미 보유하는지 확인합니다. */
    fun existsByStoreIdAndUserId(
        storeId: UUID,
        userId: UUID,
    ): Boolean

    /** 특정 상점에서 쿠폰을 많이 발급받은 유저 목록을 조회합니다. */
    @Query(
        """
        select new com.roky.casestudy.coupon.dto.CouponStoreTopUserResponse(
            c.userId,
            count(c.id)
        )
        from CouponEntity c
        where c.store.id = :storeId
        group by c.userId
        order by count(c.id) desc, c.userId asc
        """,
    )
    fun findTopIssuedUsersByStoreId(
        storeId: UUID,
        pageable: Pageable,
    ): List<CouponStoreTopUserResponse>
}

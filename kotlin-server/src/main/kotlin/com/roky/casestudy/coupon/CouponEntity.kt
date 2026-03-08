package com.roky.casestudy.coupon

import com.roky.casestudy.store.StoreEntity
import com.roky.casestudy.user.AppUserEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "coupons",
    uniqueConstraints = [UniqueConstraint(name = "uk_coupon_store_user", columnNames = ["store_id", "user_id"])],
)
class CouponEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    val store: StoreEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: AppUserEntity,

    @Column(nullable = false, updatable = false)
    val issuedAt: Instant = Instant.now(),
)

package com.roky.casestudy.coupon

import com.roky.casestudy.store.StoreEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
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
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(nullable = false, updatable = false)
    val issuedAt: Instant = Instant.now(),
)

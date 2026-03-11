package com.roky.casestudy.store

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "stores")
class StoreEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    val name: String,
    @Column(nullable = false)
    val eventTotalCount: Long = DEFAULT_EVENT_TOTAL_COUNT,
    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    companion object {
        const val DEFAULT_EVENT_TOTAL_COUNT = 300_000L
    }
}

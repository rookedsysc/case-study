package com.roky.casestudy.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "app_users")
class AppUserEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    val name: String,
    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)

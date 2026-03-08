package com.roky.casestudy.store

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.Optional
import java.util.UUID

interface StoreRepository : JpaRepository<StoreEntity, UUID> {

    /**
     * 상점을 비관적 쓰기 락으로 조회합니다.
     * 쿠폰 발행 시 동시성 제어를 위해 사용됩니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StoreEntity s WHERE s.id = :id")
    fun findByIdWithLock(id: UUID): Optional<StoreEntity>
}

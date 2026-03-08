package com.roky.casestudy.store

import com.roky.casestudy.store.dto.BulkCreateStoresResponse
import com.roky.casestudy.store.dto.CreateStoreRequest
import com.roky.casestudy.store.dto.StoreResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class StoreService(private val storeRepository: StoreRepository) {

    /** 새 상점을 생성합니다. */
    fun createStore(request: CreateStoreRequest): StoreResponse {
        val store = StoreEntity(
            name = request.name,
            eventTotalCount = request.eventTotalCount,
        )
        return StoreMapper.toResponse(storeRepository.save(store))
    }

    /**
     * 상점을 ID로 조회합니다.
     *
     * @throws NoSuchElementException 상점이 존재하지 않는 경우
     */
    fun getStore(id: UUID): StoreResponse {
        val store = storeRepository.findById(id)
            .orElseThrow { NoSuchElementException("상점을 찾을 수 없습니다: $id") }
        return StoreMapper.toResponse(store)
    }

    /**
     * 상점을 count개 일괄 생성하고 생성된 ID 목록을 반환합니다.
     * 부하 테스트 사전 데이터 생성 용도로 사용됩니다.
     *
     * @param count 생성할 상점 수 (1 이상 1000 이하)
     * @param eventTotalCount 상점별 이벤트 전체 쿠폰 발행 수량 (1 이상)
     * @returns 생성된 상점 ID 목록
     */
    fun createStoresBulk(count: Int, eventTotalCount: Long): BulkCreateStoresResponse {
        val stores = (1..count).map {
            StoreEntity(
                name = "bulk_store_${UUID.randomUUID()}",
                eventTotalCount = eventTotalCount,
            )
        }
        val savedStores = storeRepository.saveAll(stores)
        return BulkCreateStoresResponse(ids = savedStores.map { it.id })
    }
}

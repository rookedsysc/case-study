package com.roky.casestudy.store

import com.roky.casestudy.store.dto.BulkCreateStoresRequest
import com.roky.casestudy.store.dto.BulkCreateStoresResponse
import com.roky.casestudy.store.dto.CreateStoreRequest
import com.roky.casestudy.store.dto.StoreResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/stores")
class StoreController(
    private val storeService: StoreService,
) : StoreControllerDocs {
    /** 상점을 ID로 조회합니다. */
    @GetMapping("/{id}")
    override fun getStore(
        @PathVariable id: UUID,
    ): StoreResponse = storeService.getStore(id)

    /** 상점을 최대 1000개까지 일괄 생성합니다. 부하 테스트 사전 데이터 생성 용도입니다. */
    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    override fun createStoresBulk(
        @Valid @RequestBody request: BulkCreateStoresRequest,
    ): BulkCreateStoresResponse = storeService.createStoresBulk(request.count, request.eventTotalCount)
}

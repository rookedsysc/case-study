package com.roky.casestudy.store

import com.roky.casestudy.store.dto.StoreResponse

object StoreMapper {
    fun toResponse(entity: StoreEntity): StoreResponse =
        StoreResponse(
            id = entity.id,
            name = entity.name,
            eventTotalCount = entity.eventTotalCount,
            createdAt = entity.createdAt,
        )
}

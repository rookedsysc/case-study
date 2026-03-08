package com.roky.casestudy.user

import com.roky.casestudy.user.dto.AppUserResponse

object AppUserMapper {
    fun toResponse(entity: AppUserEntity): AppUserResponse = AppUserResponse(
        id = entity.id,
        name = entity.name,
        createdAt = entity.createdAt,
    )
}

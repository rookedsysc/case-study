package com.roky.casestudy.user

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AppUserRepository : JpaRepository<AppUserEntity, UUID>

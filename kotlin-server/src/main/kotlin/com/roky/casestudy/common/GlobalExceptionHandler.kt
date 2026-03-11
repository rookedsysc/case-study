package com.roky.casestudy.common

import com.roky.casestudy.coupon.exception.CouponIssueInProgressException
import com.roky.casestudy.coupon.exception.CouponLimitExceededException
import com.roky.casestudy.coupon.exception.CouponLockReleaseFailedException
import com.roky.casestudy.coupon.exception.DuplicateCouponException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(e: NoSuchElementException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(e.message ?: "리소스를 찾을 수 없습니다"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(e.message ?: "잘못된 요청입니다"))

    @ExceptionHandler(DuplicateCouponException::class)
    fun handleDuplicateCoupon(e: DuplicateCouponException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(e.message ?: "중복 쿠폰 발행 오류"))

    @ExceptionHandler(CouponLimitExceededException::class)
    fun handleCouponLimit(e: CouponLimitExceededException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.GONE).body(ErrorResponse(e.message ?: "쿠폰 수량 초과"))

    @ExceptionHandler(CouponIssueInProgressException::class)
    fun handleCouponIssueInProgress(e: CouponIssueInProgressException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(e.message ?: "쿠폰 발급 처리 중"))

    @ExceptionHandler(CouponLockReleaseFailedException::class)
    fun handleCouponLockReleaseFailed(e: CouponLockReleaseFailedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(e.message ?: "쿠폰 락 해제 실패"))
}

data class ErrorResponse(
    val message: String,
)

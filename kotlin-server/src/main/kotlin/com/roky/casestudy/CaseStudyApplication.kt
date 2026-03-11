package com.roky.casestudy

import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CaseStudyApplication

fun main(args: Array<String>) {
    runApplication<CaseStudyApplication>(*args)

    val openTelemetrySdk =
        AutoConfiguredOpenTelemetrySdk
            .builder()
            .build()
            .openTelemetrySdk
    OpenTelemetryAppender.install(openTelemetrySdk)
}

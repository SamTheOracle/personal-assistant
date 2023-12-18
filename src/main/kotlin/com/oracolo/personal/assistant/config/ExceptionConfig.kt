package com.oracolo.personal.assistant.config

import io.smallrye.config.ConfigMapping

@ConfigMapping(prefix = "personal.assistant.telegram.exception.retry")
interface ExceptionConfig {

    fun maxRedeliveries():Int

    fun redeliverDelayMs():Long
}
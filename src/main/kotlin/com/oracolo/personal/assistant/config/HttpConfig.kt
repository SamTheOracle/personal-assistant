package com.oracolo.personal.assistant.config

import io.smallrye.config.ConfigMapping
import org.eclipse.microprofile.config.inject.ConfigProperty

@ConfigMapping(prefix = "personal.assistant.telegram.http.client")
interface HttpConfig {

    fun maxConnection(): Int

    fun connectTimeoutMs(): Long

    fun socketTimeoutSecs(): Long

}
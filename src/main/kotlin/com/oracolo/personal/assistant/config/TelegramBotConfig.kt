package com.oracolo.personal.assistant.config

import io.smallrye.config.ConfigMapping

@ConfigMapping(prefix = "personal.assistant.telegram.bot")
interface TelegramBotConfig {

    fun getUpdatesUri(): String

    fun token(): String

    fun updatesLimit(): Int

    fun offsetHeaderName(): String

    fun baseUri(): String

    fun protocol(): String

    fun chatId(): String

    fun type(): String

    fun timeoutSeconds(): Long
}
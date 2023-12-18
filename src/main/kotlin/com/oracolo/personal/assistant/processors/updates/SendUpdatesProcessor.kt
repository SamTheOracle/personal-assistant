package com.oracolo.personal.assistant.processors.updates

import com.oracolo.personal.assistant.config.TelegramBotConfig
import com.oracolo.personal.assistant.processors.qualifiers.SendUpdatesProcess
import com.oracolo.personal.assistant.processors.qualifiers.TelegramUpdates
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.eclipse.microprofile.config.inject.ConfigProperty
import jakarta.enterprise.context.Dependent
import jakarta.inject.Inject

@Dependent
@SendUpdatesProcess
class SendUpdatesProcessor : Processor {

    @Inject
    lateinit var telegramBotConfig: TelegramBotConfig

    @Inject
    @TelegramUpdates
    lateinit var telegramUpdatesCurrentOffset: TelegramUpdatesCurrentOffset

    override fun process(exchange: Exchange) {
        exchange.`in`.headers[telegramBotConfig.offsetHeaderName()] =
            if (telegramUpdatesCurrentOffset.offset() == 0L) 0 else telegramUpdatesCurrentOffset.offset().inc()
    }


}
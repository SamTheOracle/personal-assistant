package com.oracolo.personal.assistant.processors.updates

import com.oracolo.personal.assistant.processors.qualifiers.SendUpdatesProcess
import com.oracolo.personal.assistant.processors.qualifiers.TelegramUpdates
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.eclipse.microprofile.config.inject.ConfigProperty
import javax.enterprise.context.Dependent
import javax.inject.Inject

@Dependent
@SendUpdatesProcess
class SendUpdatesProcessor : Processor {

    @ConfigProperty(name = "personal.assistant.telegram.offsetHeader")
    lateinit var offsetHeader: String

    @Inject
    @TelegramUpdates
    lateinit var telegramUpdatesCurrentOffset: TelegramUpdatesCurrentOffset

    override fun process(exchange: Exchange) {
        exchange.`in`.headers[offsetHeader] =
            if (telegramUpdatesCurrentOffset.offset() == 0L) 0 else telegramUpdatesCurrentOffset.offset().inc()
    }


}
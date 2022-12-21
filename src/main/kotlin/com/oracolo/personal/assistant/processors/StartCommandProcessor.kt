package com.oracolo.personal.assistant.processors

import com.oracolo.personal.assistant.processors.qualifier.StartCommandProcess
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.component.telegram.model.IncomingMessage
import org.apache.camel.component.telegram.model.OutgoingTextMessage
import org.eclipse.microprofile.config.inject.ConfigProperty
import javax.enterprise.context.Dependent

@Dependent
@StartCommandProcess
class StartCommandProcessor : Processor {

    @ConfigProperty(name = "personal.assistant.telegram.command.start.message.namePlaceholder")
    lateinit var namePlaceholder: String

    @ConfigProperty(name = "personal.assistant.telegram.command.start.message")
    lateinit var startMessage: String
    override fun process(exchange: Exchange) {
        val incomingMessage = exchange.`in`.body as IncomingMessage
        exchange.`in`.body = OutgoingTextMessage().apply {
            text = startMessage.replace(
                namePlaceholder,
                " ${incomingMessage.from?.firstName ?: incomingMessage.from?.username ?: "user"}"
            )
        }
    }
}
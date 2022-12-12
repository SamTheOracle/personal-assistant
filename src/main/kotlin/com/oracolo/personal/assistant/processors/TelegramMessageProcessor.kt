package com.oracolo.personal.assistant.processors

import com.oracolo.personal.assistant.handlers.CommandHandler
import com.oracolo.personal.assistant.model.Command
import io.quarkus.arc.All
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.component.telegram.model.IncomingMessage
import org.apache.camel.component.telegram.model.OutgoingTextMessage
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpRequest
import javax.enterprise.context.Dependent
import javax.inject.Inject

@Dependent
class TelegramMessageProcessor : Processor {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Inject
    @All
    lateinit var commandHandlers: MutableList<CommandHandler>

    override fun process(exchange: Exchange) {
        val incomingMessage = exchange.`in`.body as IncomingMessage
        logger.info("Received {}.", incomingMessage)

        val commandHandler = commandHandlers.find { it.canHandle(Command.from(incomingMessage.text)) }

        exchange.`in`.body = commandHandler?.handle(incomingMessage)
    }

}
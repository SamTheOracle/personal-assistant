package com.oracolo.personal.assistant

import org.apache.camel.Predicate
import org.apache.camel.Processor
import org.apache.camel.RoutesBuilder
import org.apache.camel.builder.endpoint.StaticEndpointBuilders.direct
import org.apache.camel.builder.endpoint.StaticEndpointBuilders.telegram
import org.apache.camel.component.telegram.TelegramConstants
import org.apache.camel.component.telegram.model.IncomingMessage
import org.apache.camel.component.telegram.model.IncomingMessageEntity
import org.apache.camel.component.telegram.model.MessageResult
import org.apache.camel.component.telegram.model.OutgoingTextMessage
import org.apache.camel.quarkus.kotlin.routes
import org.eclipse.microprofile.config.inject.ConfigProperty
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.inject.Produces
import javax.inject.Inject

@ApplicationScoped
class TelegramIntegration {

    @ConfigProperty(name = "personal.assistant.telegram.bot.token")
    lateinit var botToken: String

    @ConfigProperty(name = "personal.assistant.telegram.chatId")
    lateinit var chatId: String

    @ConfigProperty(name = "personal.assistant.telegram.bot.poll-delay")
    lateinit var pollDelay: String

    @ConfigProperty(name = "personal.assistant.telegram.bot.updates-limit")
    lateinit var updatesLimit: String

    @ConfigProperty(name = "personal.assistant.telegram.wire-tap")
    lateinit var wireTapRoute: String

    @Inject
    lateinit var telegramMessageProcessor: Processor

    @Produces
    fun telegramToTelegram(): RoutesBuilder = routes {
        from(
            telegram(TELEGRAM_TYPE).authorizationToken(botToken)
                .delay(pollDelay)
                .limit(updatesLimit)
        ).filter {
            true == (it.`in`.body as? IncomingMessage)?.text?.isNotBlank()
        }
            .wireTap(direct(wireTapRoute))
            .choice()
            .`when`(header(TelegramConstants.TELEGRAM_CHAT_ID).isEqualTo(chatId))
            .endChoice()
            .otherwise()
            .process(telegramMessageProcessor)
            .to(
                telegram(TELEGRAM_TYPE).authorizationToken(botToken)
                    .chatId("\${header.${TelegramConstants.TELEGRAM_CHAT_ID}}")
            )
            .end()

        from(direct(wireTapRoute))
            .process {
                it.`in`.body = OutgoingTextMessage().apply {
                    text = """Someone has interest in your bot and wrote:
                    |${(it.`in`.body as IncomingMessage).text}
                """.trimMargin()
                }

            }
            .to(
                telegram(TELEGRAM_TYPE).authorizationToken(botToken)
                    .chatId(chatId)
            ).end()
    }

    companion object {
        private const val TELEGRAM_TYPE = "bots"
    }
}
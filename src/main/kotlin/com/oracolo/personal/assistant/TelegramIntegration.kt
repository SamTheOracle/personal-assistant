package com.oracolo.personal.assistant

import com.oracolo.personal.assistant.model.Command
import com.oracolo.personal.assistant.processors.qualifier.StartCommandProcess
import org.apache.camel.Processor
import org.apache.camel.RoutesBuilder
import org.apache.camel.builder.endpoint.StaticEndpointBuilders.direct
import org.apache.camel.builder.endpoint.StaticEndpointBuilders.telegram
import org.apache.camel.component.telegram.TelegramConstants
import org.apache.camel.component.telegram.model.IncomingMessage
import org.apache.camel.component.telegram.model.OutgoingTextMessage
import org.apache.camel.quarkus.kotlin.routes
import org.eclipse.microprofile.config.inject.ConfigProperty
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.inject.Produces

@ApplicationScoped
class TelegramIntegration {

    @Produces
    fun telegramToTelegram(
        @ConfigProperty(name = "personal.assistant.telegram.bot.token") botToken: String,
        @ConfigProperty(name = "personal.assistant.telegram.chatId") chatId: String,
        @ConfigProperty(name = "personal.assistant.telegram.bot.pollDelay") pollDelay: String,
        @ConfigProperty(name = "personal.assistant.telegram.bot.updatesLimits") updatesLimit: String,
        @ConfigProperty(name = "personal.assistant.telegram.wireTap") wireTapRoute: String,
        @ConfigProperty(name = "personal.assistant.telegram.retry.maxRedeliveries") maxRedeliveries: Int,
        @ConfigProperty(name = "personal.assistant.telegram.retry.redeliverDelay") redeliverDelay: Long,
        @ConfigProperty(name = "personal.assistant.telegram.command.website.route") websiteCommandRoute: String,
        @ConfigProperty(name = "personal.assistant.telegram.command.start.route") startCommandRoute: String,
        @ConfigProperty(name = "personal.assistant.telegram.command.default.route") defaultCommandRoute: String,
        @ConfigProperty(name = "personal.assistant.telegram.command.appointment.route") appointmentCommandRoute: String,
        @ConfigProperty(name = "personal.assistant.telegram.command.curriculum.route") curriculumCommandRoute: String,
        @ConfigProperty(name = "personal.assistant.telegram.final.route") finalRoute: String,
        @ConfigProperty(name = "personal.assistant.telegram.type") telegramComponentType: String,
        @StartCommandProcess
        startCommandProcessor: Processor
    ): RoutesBuilder = routes {

        onException(Exception::class.java)
            .maximumRedeliveries(maxRedeliveries)
            .useExponentialBackOff()
            .redeliveryDelay(redeliverDelay)
            .handled(true)
            .end()

        from(
            telegram(telegramComponentType).authorizationToken(botToken)
                .delay(pollDelay)
                .limit(updatesLimit)
        ).wireTap(direct(wireTapRoute))
            .log("Received \${body}")
            .filter {
            true == (it.`in`.body as? IncomingMessage)?.text?.isNotBlank()
        }.choice()
                .`when` { Command.WEBSITE == Command.from((it.`in`.body as? IncomingMessage)?.text) }
                    .to(direct(websiteCommandRoute))
                .`when` { Command.START == Command.from((it.`in`.body as? IncomingMessage)?.text) }
                    .to(direct(startCommandRoute))
                .`when` { Command.CURRICULUM == Command.from((it.`in`.body as? IncomingMessage)?.text) }
                    .to(direct(curriculumCommandRoute))
                .`when` { Command.APPOINTMENT == Command.from((it.`in`.body as? IncomingMessage)?.text) }
                    .to(direct(appointmentCommandRoute))
            .otherwise()
                .to(direct(defaultCommandRoute))
            .end()

        from(direct(websiteCommandRoute))
            .toD(
                telegram(telegramComponentType).authorizationToken(botToken)
                    .chatId("\${header.${TelegramConstants.TELEGRAM_CHAT_ID}}")
            )
            .end()

        from(direct(startCommandRoute))
            .process(startCommandProcessor)
            .to(direct(finalRoute))
            .end()

        from(direct(defaultCommandRoute))
            .to(direct(finalRoute))
            .end()

        from(direct(curriculumCommandRoute))
            .to(direct(finalRoute))
            .end()

        from(direct(appointmentCommandRoute))
            .to(direct(finalRoute))
            .end()

        from(direct(finalRoute))
            .toD(
                telegram(telegramComponentType).authorizationToken(botToken)
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
                telegram(telegramComponentType).authorizationToken(botToken)
                    .chatId(chatId)
            ).end()
    }

}
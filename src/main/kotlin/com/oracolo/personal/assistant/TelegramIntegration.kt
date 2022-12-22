package com.oracolo.personal.assistant

import com.oracolo.personal.assistant.model.Command
import org.apache.camel.Processor
import org.apache.camel.RoutesBuilder
import org.apache.camel.builder.endpoint.StaticEndpointBuilders.direct
import org.apache.camel.builder.endpoint.StaticEndpointBuilders.telegram
import org.apache.camel.component.http.HttpConstants
import org.apache.camel.component.telegram.TelegramConstants
import org.apache.camel.component.telegram.TelegramMediaType
import org.apache.camel.component.telegram.model.IncomingMessage
import org.apache.camel.component.telegram.model.OutgoingDocumentMessage
import org.apache.camel.component.telegram.model.OutgoingTextMessage
import org.apache.camel.http.common.HttpMethods
import org.apache.camel.quarkus.kotlin.routes
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.io.InputStream
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
        @ConfigProperty(name = "personal.assistant.telegram.command.default.message") defaultMessage: String,
        @ConfigProperty(name = "personal.assistant.telegram.command.appointment.route") appointmentCommandRoute: String,
        @ConfigProperty(name = "personal.assistant.telegram.command.curriculum.route") curriculumCommandRoute: String,
        @ConfigProperty(name = "personal.assistant.telegram.final.route") finalRoute: String,
        @ConfigProperty(name = "personal.assistant.telegram.type") telegramComponentType: String,
        @ConfigProperty(name = "personal.assistant.telegram.command.website.message") websiteCommandMessage: String,
        @ConfigProperty(name = "personal.assistant.telegram.command.start.message.namePlaceholder") namePlaceholder: String,
        @ConfigProperty(name = "personal.assistant.telegram.command.start.message.defaultName") defaultName: String,
        @ConfigProperty(name = "personal.assistant.telegram.command.start.message") startMessage: String,
        @ConfigProperty(name = "personal.assistant.telegram.command.appointment.calendar-url") calendarUrl: String,
        @ConfigProperty(name = "personal.assistant.telegram.command.curriculum.url") curriculumUrl: String,
        @ConfigProperty(name = "personal.assistant.telegram.command.curriculum.fileName") curriculumFileName: String,
        @ConfigProperty(name = "personal.assistant.telegram.base.url") telegramBaseUrl:String
    ): RoutesBuilder = routes {

        onException(Exception::class.java)
            .log("Exception \${exception}")
            .maximumRedeliveries(maxRedeliveries)
            .useExponentialBackOff()
            .redeliveryDelay(redeliverDelay)
            .handled(false)
            .log("Body in exception \${body}")
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
                .`when` { Command.CALENDAR == Command.from((it.`in`.body as? IncomingMessage)?.text) }
                    .to(direct(appointmentCommandRoute))
                .otherwise()
                    .to(direct(defaultCommandRoute))
            .end()

        from(direct(websiteCommandRoute))
            .process(textProcessor(websiteCommandMessage, false))
            .toD(
                telegram(telegramComponentType).authorizationToken(botToken)
                    .chatId("\${header.${TelegramConstants.TELEGRAM_CHAT_ID}}")
            )
            .end()

        from(direct(startCommandRoute))
            .process {
                val message = (it.`in`.body as IncomingMessage).let { incomingMessage ->
                    startMessage.replace(
                        namePlaceholder,
                        incomingMessage.from?.firstName ?: incomingMessage.from?.username ?: defaultName
                    )
                }
                textProcessor(message)
            }
            .to(direct(finalRoute))
            .end()

        from(direct(defaultCommandRoute))
            .process(textProcessor(defaultMessage))
            .to(direct(finalRoute))
            .end()

        from(direct(curriculumCommandRoute))
            .to("$telegramBaseUrl/sendDocument?chat_id=$chatId&document=$curriculumUrl")
            .end()

        from(direct(appointmentCommandRoute))
            .process(textProcessor(calendarUrl, false))
            .to(direct(finalRoute))
            .end()

        from(direct(finalRoute))
            .toD(
                telegram(telegramComponentType).authorizationToken(botToken)
                    .chatId("\${header.${TelegramConstants.TELEGRAM_CHAT_ID}}")
            )
            .end()


        from(direct(wireTapRoute))
            .setHeader(TelegramConstants.TELEGRAM_CHAT_ID, simple(chatId))
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

    companion object {
        fun textProcessor(message: String, disablePreview: Boolean = true): Processor = Processor {
            it.`in`.body = OutgoingTextMessage().apply {
                text = message
                disableWebPagePreview = disablePreview
            }
        }
    }

}
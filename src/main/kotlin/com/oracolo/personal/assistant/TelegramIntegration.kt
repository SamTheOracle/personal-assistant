package com.oracolo.personal.assistant

import com.oracolo.personal.assistant.model.Command
import com.oracolo.personal.assistant.processors.qualifiers.SendUpdatesProcess
import com.oracolo.personal.assistant.processors.qualifiers.UpdatesProcess
import io.netty.handler.codec.http.HttpHeaderValues
import io.quarkus.runtime.Startup
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import org.apache.camel.Processor
import org.apache.camel.RoutesBuilder
import org.apache.camel.builder.endpoint.StaticEndpointBuilders.*
import org.apache.camel.component.telegram.TelegramConstants
import org.apache.camel.component.telegram.model.IncomingMessage
import org.apache.camel.component.telegram.model.OutgoingTextMessage
import org.apache.camel.component.telegram.model.Update
import org.apache.camel.http.common.HttpMethods
import org.apache.camel.quarkus.kotlin.routes
import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
import org.apache.hc.core5.http.ContentType
import org.apache.http.HttpHeaders
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.io.File
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit

@ApplicationScoped
@Startup
class TelegramIntegration {

    lateinit var fileAsBinary: ByteArray

    @ConfigProperty(name = "personal.assistant.telegram.command.curriculum.url")
    lateinit var fileUrl: String

    @PostConstruct
    fun loadFile() {
        fileAsBinary = javaClass.classLoader.getResourceAsStream(fileUrl)?.readAllBytes()
            ?: throw RuntimeException("Where is file?")
    }

    @Produces
    fun schedulerRoute(
        @ConfigProperty(name = "personal.assistant.scheduler.name") schedulerName: String,
        @ConfigProperty(name = "personal.assistant.telegram.bot.pollDelay") pollDelay: Long,
        @ConfigProperty(name = "personal.assistant.telegram.updates.route") updatesRoute: String,
        @ConfigProperty(name = "personal.assistant.telegram.uri") telegramBaseUrl: String,
        @ConfigProperty(name = "personal.assistant.telegram.protocol") protocol: String,
        @ConfigProperty(name = "personal.assistant.telegram.updatesCommand") updatesCommand: String,
        @ConfigProperty(name = "personal.assistant.telegram.bot.timeout") timeout: Int,
        @ConfigProperty(name = "personal.assistant.telegram.bot.updatesLimits") updatesLimit: String,
        @ConfigProperty(name = "personal.assistant.telegram.incomingUpdateRoute") incomingUpdateRoute: String,
        @ConfigProperty(name = "personal.assistant.telegram.offsetHeader") offsetHeader: String,
        httpClient: HttpClient,
        @UpdatesProcess
        updatesProcessor: Processor,
        @SendUpdatesProcess
        sendUpdatesProcessor: Processor
    ) = routes {

        onException(Exception::class.java)
            .log("Received exception during scheduling \${exception}")
            .handled(true)
            .end()

        from(
            scheduler(schedulerName)
                .timeUnit(TimeUnit.SECONDS)
                .delay(pollDelay)
                .useFixedDelay(true)
        ).process(sendUpdatesProcessor)
            .log(
                "Scheduler in action! Current offset \${header.$offsetHeader}"
            )
            .log("Making request to $protocol://$telegramBaseUrl/$updatesCommand?timeout=$timeout&limit=$updatesLimit&offset=\${header.$offsetHeader}")
            .toD(
                http(
                    protocol,
                    "$telegramBaseUrl/$updatesCommand?timeout=$timeout&limit=$updatesLimit&offset=\${header.$offsetHeader}"
                ).advanced().httpClient(httpClient)
            ).convertBodyTo(String::class.java)
            .process(updatesProcessor)
            .split(body())
            .process {
                it.`in`.headers[TelegramConstants.TELEGRAM_CHAT_ID] = (it.`in`.body as Update).message.chat.id
            }
            .to(direct(incomingUpdateRoute))
            .end()

    }

    @Produces
    fun httpClient(
        @ConfigProperty(name = "personal.assistant.http.client.maxConnection") maxConnection: Int,
        @ConfigProperty(name = "personal.assistant.http.client.connectTimeout") connectTimeout: Long,
        @ConfigProperty(name = "personal.assistant.http.client.socketTimeout") socketTimeout: Long
    ): HttpClient = HttpClientBuilder.create()
        .setConnectionManager(PoolingHttpClientConnectionManager().apply {
            maxTotal = maxConnection
        })
        .disableRedirectHandling()
        .disableAutomaticRetries()
        .setDefaultRequestConfig(
            RequestConfig.custom()
                .setConnectionRequestTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .setResponseTimeout(socketTimeout, TimeUnit.MILLISECONDS)
                .build()
        )
        .build()


    @Produces
    fun telegramToTelegram(
        @ConfigProperty(name = "personal.assistant.telegram.bot.token") botToken: String,
        @ConfigProperty(name = "personal.assistant.telegram.chatId") chatId: String,
        @ConfigProperty(name = "personal.assistant.telegram.bot.pollDelay") pollDelay: Long,
        @ConfigProperty(name = "personal.assistant.telegram.bot.timeout") timeout: Int,
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
        @ConfigProperty(name = "personal.assistant.telegram.incomingUpdateRoute") incomingUpdateRoute: String,
        @ConfigProperty(name = "personal.assistant.telegram.uri") telegramBaseUrl: String,
        @ConfigProperty(name = "personal.assistant.telegram.protocol") protocol: String,
        @ConfigProperty(name = "personal.assistant.telegram.command.form.keys.document") documentKey: String,
        @ConfigProperty(name = "personal.assistant.telegram.command.form.keys.chatId") chatIdKey: String,
        @ConfigProperty(name = "personal.assistant.telegram.command.curriculum.fileName") curriculumName: String,
        httpClient: HttpClient
    ): RoutesBuilder = routes {

        onException(Exception::class.java)
            .log("Exception \${exception}")
            .maximumRedeliveries(maxRedeliveries)
            .useExponentialBackOff()
            .redeliveryDelay(redeliverDelay)
            .handled(true)
            .log("Body in exception \${body}")
            .end()


        from(
            direct(incomingUpdateRoute)
        ).log("Received \${body}")
            .filter {
                true == (it.`in`.body as? Update)?.message?.text?.isNotBlank()
            }
            .process {
                it.`in`.body = (it.`in`.body as Update).message
            }
            .wireTap(direct(wireTapRoute))
            .choice()
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
                it.`in`.body = (it.`in`.body as IncomingMessage).let { incomingMessage ->
                    startMessage.replace(
                        namePlaceholder,
                        incomingMessage.from?.firstName ?: incomingMessage.from?.username ?: defaultName
                    )
                }
            }
            .log("Headers \${headers} and body \${body}")
            .to(direct(finalRoute))
            .end()

        from(direct(defaultCommandRoute))
            .process(textProcessor(defaultMessage))
            .to(direct(finalRoute))
            .end()

        from(direct(curriculumCommandRoute))
            .setHeader(HttpHeaders.CONTENT_TYPE, simple("${HttpHeaderValues.MULTIPART_FORM_DATA}"))
            .setBody {
                MultipartEntityBuilder.create()
                    .addBinaryBody(documentKey,fileAsBinary, ContentType.APPLICATION_PDF, curriculumName)
                    .addTextBody(chatIdKey, chatId)
                    .build()
            }
            .toD(
                http(
                    protocol,
                    "$telegramBaseUrl/sendDocument?chat_id=$chatId"
                )
                    .httpMethod(HttpMethods.POST)
                    .advanced()
                    .httpClient(httpClient)
            )
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
                it.`in`.body = (it.`in`.body as IncomingMessage).let {
                    OutgoingTextMessage().apply {
                        text =
                            """${it.from?.username ?: it.from?.firstName ?: "Someone"} has interest in your bot and wrote:
                    |${it.text}
                """.trimMargin()
                    }
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
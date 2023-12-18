package com.oracolo.personal.assistant

import com.oracolo.personal.assistant.config.ExceptionConfig
import com.oracolo.personal.assistant.config.HttpConfig
import com.oracolo.personal.assistant.config.RouteConfig
import com.oracolo.personal.assistant.config.TelegramBotConfig
import com.oracolo.personal.assistant.model.Command
import com.oracolo.personal.assistant.processors.qualifiers.SendUpdatesProcess
import com.oracolo.personal.assistant.processors.qualifiers.UpdatesProcess
import com.oracolo.personal.assistant.processors.updates.TelegramUpdatesCurrentOffset
import io.netty.handler.codec.http.HttpHeaderValues
import io.quarkus.runtime.Startup
import jakarta.annotation.PostConstruct
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton
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
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit

@Singleton
@Startup
class TelegramIntegration {

    private lateinit var fileAsBinary: ByteArray


    @Inject
    lateinit var routeConfig: RouteConfig
    
    @Inject
    lateinit var telegramBotConfig: TelegramBotConfig
    
    @Inject
    lateinit var exceptionConfig: ExceptionConfig

    
    @Inject
    @UpdatesProcess
    lateinit var updatesProcessor: Processor
    
    @Inject
    @SendUpdatesProcess
    lateinit var sendUpdatesProcessor: Processor

    @Inject
    lateinit var httpConfig: HttpConfig
    
    
    @PostConstruct
    fun loadFile() {
        fileAsBinary = javaClass.classLoader.getResourceAsStream(routeConfig.curriculum().url())?.readAllBytes()
            ?: throw RuntimeException("Where is file?")
    }

    @Produces
    fun schedulerRoute(httpClient: HttpClient) = routes {

        onException(Exception::class.java)
            .log("Received exception during scheduling \${exception}")
            .handled(true)
            .end()


        from(
            scheduler(routeConfig.scheduler().name())
                .timeUnit(TimeUnit.SECONDS)
                .delay(routeConfig.scheduler().pollDelaySecs())
                .useFixedDelay(true)
        ).process(sendUpdatesProcessor)
            .log(
                "Scheduler in action! Current offset \${header.${telegramBotConfig.offsetHeaderName()}}"
            )
            .toD(
                http(
                    telegramBotConfig.protocol(),
                    "${telegramBotConfig.getUpdatesUri()}&offset=\${header.${telegramBotConfig.offsetHeaderName()}}"
                ).advanced().httpClient(httpClient)
            ).convertBodyTo(String::class.java)
            .process(updatesProcessor)
            .split(body())
            .process {
                it.`in`.headers[TelegramConstants.TELEGRAM_CHAT_ID] = (it.`in`.body as Update).message.chat.id
            }
            .to(direct(routeConfig.incomingUpdate().name()))
            .end()

    }

    @Produces
    fun httpClient(): HttpClient = HttpClientBuilder.create()
        .setConnectionManager(PoolingHttpClientConnectionManager().apply {
            maxTotal = httpConfig.maxConnection()
        })
        .disableRedirectHandling()
        .disableAutomaticRetries()
        .setDefaultRequestConfig(
            RequestConfig.custom()
                .setConnectionRequestTimeout(httpConfig.connectTimeoutMs(), TimeUnit.MILLISECONDS)
                .setResponseTimeout(httpConfig.socketTimeoutSecs(), TimeUnit.SECONDS)
                .build()
        )
        .build()


    @Produces
    fun telegramToTelegram(httpClient: HttpClient): RoutesBuilder = routes {

        onException(Exception::class.java)
            .log("Exception \${exception}")
            .maximumRedeliveries(exceptionConfig.maxRedeliveries())
            .useExponentialBackOff()
            .redeliveryDelay(exceptionConfig.redeliverDelayMs())
            .handled(true)
            .log("Body in exception \${body}")
            .end()


        from(
            direct(routeConfig.incomingUpdate().name())
        ).log("Received \${body}")
            .filter {
                true == (it.`in`.body as? Update)?.message?.text?.isNotBlank()
            }
            .process {
                it.`in`.body = (it.`in`.body as Update).message
            }
            .wireTap(direct(routeConfig.wiretap().name()))
            .choice()
                .`when` { Command.WEBSITE == Command.from((it.`in`.body as? IncomingMessage)?.text) }
                    .to(direct(routeConfig.website().name()))
                .`when` { Command.START == Command.from((it.`in`.body as? IncomingMessage)?.text) }
                    .to(direct(routeConfig.start().name()))
                .`when` { Command.CURRICULUM == Command.from((it.`in`.body as? IncomingMessage)?.text) }
                    .to(direct(routeConfig.curriculum().name()))
                .`when` { Command.CALENDAR == Command.from((it.`in`.body as? IncomingMessage)?.text) }
                    .to(direct(routeConfig.calendar().name()))
            .otherwise()
                .to(direct(routeConfig.default().name()))
            .end()

        from(direct(routeConfig.website().name()))
            .process(textProcessor(routeConfig.website().message(), false))
            .toD(
                telegram(telegramBotConfig.type()).authorizationToken(telegramBotConfig.token())
                    .chatId("\${header.${TelegramConstants.TELEGRAM_CHAT_ID}}")
            )
            .end()

        from(direct(routeConfig.start().name()))
            .process {
                it.`in`.body = (it.`in`.body as IncomingMessage).let { incomingMessage ->
                    routeConfig.start().message().replace(
                        routeConfig.start().placeholderName(),
                        incomingMessage.from?.firstName ?: incomingMessage.from?.username ?: routeConfig.start().defaultName()
                    )
                }
            }
            .log("Headers \${headers} and body \${body}")
            .to(direct(routeConfig.final().name()))
            .end()

        from(direct(routeConfig.default().name()))
            .process(textProcessor(routeConfig.default().message()))
            .to(direct(routeConfig.final().name()))
            .end()

        from(direct(routeConfig.curriculum().name()))
            .setHeader(HttpHeaders.CONTENT_TYPE, simple("${HttpHeaderValues.MULTIPART_FORM_DATA}"))
            .setBody {
                MultipartEntityBuilder.create()
                    .addBinaryBody(routeConfig.curriculum().form().documentKey(), fileAsBinary, ContentType.APPLICATION_PDF, routeConfig.curriculum().fileName())
                    .addTextBody(routeConfig.curriculum().form().chatIdKey(), it.`in`.headers[TelegramConstants.TELEGRAM_CHAT_ID] as String)
                    .build()
            }
            .log("Sending cv to chat id \${header.${TelegramConstants.TELEGRAM_CHAT_ID}} ")
            .toD(
                http(
                    telegramBotConfig.protocol(),
                    "${telegramBotConfig.baseUri()}/sendDocument?chat_id=\${header.${TelegramConstants.TELEGRAM_CHAT_ID}}"
                )
                    .httpMethod(HttpMethods.POST)
                    .advanced()
                    .httpClient(httpClient)
            )
            .end()

        from(direct(routeConfig.calendar().name()))
            .process(textProcessor(routeConfig.calendar().calendlyUrl(), false))
            .to(direct(routeConfig.final().name()))
            .end()

        from(direct(routeConfig.final().name()))
            .toD(
                telegram(telegramBotConfig.type()).authorizationToken(telegramBotConfig.token())
                    .chatId("\${header.${TelegramConstants.TELEGRAM_CHAT_ID}}")
            )
            .end()


        from(direct(routeConfig.wiretap().name()))
            .choice()
            .`when` { telegramBotConfig.chatId() != (it.`in`.headers[TelegramConstants.TELEGRAM_CHAT_ID] as? String) }
            .setHeader(TelegramConstants.TELEGRAM_CHAT_ID, simple(telegramBotConfig.chatId()))
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
                telegram(telegramBotConfig.type()).authorizationToken(telegramBotConfig.token())
                    .chatId(telegramBotConfig.chatId())
            )
            .end()
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
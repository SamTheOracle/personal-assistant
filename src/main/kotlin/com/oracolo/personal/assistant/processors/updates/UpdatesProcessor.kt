package com.oracolo.personal.assistant.processors.updates

import com.fasterxml.jackson.databind.ObjectMapper
import com.oracolo.personal.assistant.processors.qualifiers.TelegramUpdates
import com.oracolo.personal.assistant.processors.qualifiers.UpdatesProcess
import io.quarkus.arc.Lock
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.component.telegram.model.UpdateResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.annotation.PostConstruct
import javax.inject.Inject
import javax.inject.Singleton

//Singleton has no client proxy, so we can safely read/write fields
@Singleton
@Lock
@UpdatesProcess
@TelegramUpdates
class UpdatesProcessor : Processor, TelegramUpdatesCurrentOffset {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Inject
    lateinit var objectMapper: ObjectMapper

    private var offset: Long = 0

    @PostConstruct
    fun construct() {
        logger.info("I have been created")
    }


    override fun process(exchange: Exchange) {
        val updates =
            (exchange.`in`.body as? String)?.let {
                objectMapper.readValue(
                    it,
                    UpdateResult::class.java
                ).updates.filterNotNull()
            }
        exchange.`in`.body = updates
        offset = updates?.maxOfOrNull { it.updateId } ?: offset
    }

    override fun offset(): Long = offset
}
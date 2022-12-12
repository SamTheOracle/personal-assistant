package com.oracolo.personal.assistant.handlers

import com.oracolo.personal.assistant.model.Command
import com.oracolo.personal.assistant.model.CommandResult
import org.apache.camel.component.telegram.model.IncomingMessage
import org.apache.camel.component.telegram.model.OutgoingMessage

interface CommandHandler {

    fun handle(incomingMessage: IncomingMessage): OutgoingMessage

    fun canHandle(command: Command?): Boolean
}
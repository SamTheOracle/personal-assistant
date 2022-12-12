package com.oracolo.personal.assistant.handlers

import com.oracolo.personal.assistant.model.Command
import org.apache.camel.component.telegram.model.IncomingMessage
import org.apache.camel.component.telegram.model.OutgoingMessage
import org.apache.camel.component.telegram.model.OutgoingTextMessage
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class DefaultCommandHandler : CommandHandler {
    override fun handle(incomingMessage: IncomingMessage): OutgoingMessage {
        return OutgoingTextMessage().apply {
            text =
                """Dear ${incomingMessage.from?.firstName ?: incomingMessage.from?.username ?: "user"}, thanks for reaching out to me.
                |I'm not really useful at the moment, but soon I will be!
            """.trimMargin()
        }
    }

    override fun canHandle(command: Command?): Boolean {
      return null == command
    }
}
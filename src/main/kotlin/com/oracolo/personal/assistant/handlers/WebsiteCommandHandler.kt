package com.oracolo.personal.assistant.handlers

import com.oracolo.personal.assistant.model.Command
import org.apache.camel.component.telegram.TelegramParseMode
import org.apache.camel.component.telegram.model.IncomingMessage
import org.apache.camel.component.telegram.model.OutgoingMessage
import org.apache.camel.component.telegram.model.OutgoingTextMessage
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class WebsiteCommandHandler : CommandHandler {
    override fun handle(incomingMessage: IncomingMessage): OutgoingMessage {
        return OutgoingTextMessage().apply {
            text =
                "Please, refer to my <a href=\"https://core.telegram.org/bots/api#sendmessage\">website</a>"
            parseMode = TelegramParseMode.HTML.code

        }
    }

    override fun canHandle(command: Command?): Boolean {
        return Command.WEBSITE == command
    }
}
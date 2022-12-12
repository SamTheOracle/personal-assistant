package com.oracolo.personal.assistant.handlers

import com.oracolo.personal.assistant.model.Command
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class StartCommandHandler(val defaultCommandHandler: DefaultCommandHandler) : CommandHandler by defaultCommandHandler {
    override fun canHandle(command: Command?): Boolean {
        return Command.START == command
    }
}
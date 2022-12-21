package com.oracolo.personal.assistant.model

import org.apache.camel.component.telegram.model.IncomingMessageEntity

enum class Command (val code:String){
    START("/start"), WEBSITE("/website"), CURRICULUM("/cv"), APPOINTMENT("/appointment");

    companion object {
        fun from(text: String?): Command? {
            return values().firstOrNull { it.code == text || it.name == text }
        }
    }
}
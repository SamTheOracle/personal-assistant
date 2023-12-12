package com.oracolo.personal.assistant.model

enum class Command (val code:String){
    START("/start"), WEBSITE("/website"), CURRICULUM("/curriculum"), CALENDAR("/calendar");

    companion object {
        fun from(text: String?): Command? {
            return entries.firstOrNull { it.code == text || it.name == text }
        }
    }
}
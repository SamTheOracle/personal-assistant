package com.oracolo.personal.assistant.model

enum class Command (val code:String){
    START("/start"), WEBSITE("/website"), CURRICULUM("/curriculum"), CALENDAR("/calendar");

    companion object {
        fun from(text: String?): Command? {
            return values().firstOrNull { it.code == text || it.name == text }
        }
    }
}
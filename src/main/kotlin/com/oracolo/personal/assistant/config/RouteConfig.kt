package com.oracolo.personal.assistant.config

import io.smallrye.config.ConfigMapping

@ConfigMapping(prefix = "personal.assistant.telegram.route")
interface RouteConfig {

    fun scheduler(): SchedulerRouteConfig

    fun incomingUpdate(): IncomingUpdateRouteConfig

    fun website(): WebsiteRouteConfig

    fun start(): StartRouteConfig

    fun curriculum(): CurriculumRouteConfig

    fun calendar(): CalendarRouteConfig

    fun default(): DefaultRouteConfig

    fun wiretap(): WiretapRouteConfig

    fun final(): FinalRouteConfig
}

@ConfigMapping(prefix = "final")
interface FinalRouteConfig {
    fun name(): String
}

@ConfigMapping(prefix = "name")
interface WiretapRouteConfig {
    fun name(): String
}

@ConfigMapping(prefix = "calendar")
interface CalendarRouteConfig {

    fun name(): String

    fun calendlyUrl(): String
}

@ConfigMapping(prefix = "default")
interface DefaultRouteConfig {

    fun name(): String

    fun message(): String
}

@ConfigMapping(prefix = "curriculum")
interface CurriculumRouteConfig {
    fun name(): String

    fun url(): String

    fun fileName(): String

    fun form(): CurriculumRouteFormConfig
}

@ConfigMapping(prefix = "form")
interface CurriculumRouteFormConfig {

    fun documentKey(): String
    fun chatIdKey(): String
}

@ConfigMapping(prefix = "start")
interface StartRouteConfig {

    fun name(): String

    fun placeholderName(): String

    fun defaultName(): String

    fun message(): String

}

@ConfigMapping(prefix = "incoming-update")
interface IncomingUpdateRouteConfig {

    fun name(): String
}

@ConfigMapping(prefix = "scheduler")
interface SchedulerRouteConfig {
    fun name(): String
    fun pollDelaySecs(): Long
}

@ConfigMapping(prefix = "website")
interface WebsiteRouteConfig {
    fun name(): String
    fun message(): String
}
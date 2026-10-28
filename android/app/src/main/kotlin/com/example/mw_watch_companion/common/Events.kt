package com.example.mw_watch_companion.common

object Events {
    val bus = EventBus()
}

sealed interface Event {
    data class MapFrameSend(
        val payload: UByteArray
    ) : Event

    data class NotificationSend(
        val payload: UByteArray
    ) : Event

    data object NavigationStart : Event

    data object NavigationEnd : Event

    data class Error(
        val source: String,
        val message: String
    ) : Event
}

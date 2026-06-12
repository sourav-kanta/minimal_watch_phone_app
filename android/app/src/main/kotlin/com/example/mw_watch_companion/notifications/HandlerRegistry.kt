package com.example.mw_watch_companion.notifications

object HandlerRegistry {
    val handlers: List<NotificationHandler> = listOf(
        WhatsAppHandler(),
        MessagingHandler(),
        NavigationHandler(),
        CallHandler()
    )
}
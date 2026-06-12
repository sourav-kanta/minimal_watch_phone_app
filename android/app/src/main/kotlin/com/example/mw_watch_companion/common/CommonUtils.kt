package com.example.mw_watch_companion.common

enum class PhoneApp(val id: Byte) {
    WHATSAPP(0),
    MESSAGE(1),
    NAVIGATION(2),
    CALL(3),
    UNKNOWN(4);

    companion object {
        fun fromId(id: Byte): PhoneApp = values().find { it.id == id } ?: UNKNOWN
    }
}

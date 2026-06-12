package com.example.mw_watch_companion.common

interface DataIngestor<T> {
    fun ingest(item: T)
}
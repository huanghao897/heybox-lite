package com.ronan.heyboxlite

import org.json.JSONObject

/** Immutable action metadata for one route entry, derived from its owned detail state. */
internal data class ComposeDetailContext(
    val entryKey: Long,
    val item: FeedItem,
    val body: JSONObject?,
    val hsrc: String,
    val authCode: String,
    val diagnostics: String,
)

internal fun ComposeDetailState.actionContext() = ComposeDetailContext(
    entryKey, item, body, Json.first(hsrc, item.hsrc), authCode, diagnostics,
)

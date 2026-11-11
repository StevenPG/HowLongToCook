package com.stevenpg.howlongtocook.util

import kotlinx.serialization.json.Json

/** Single JSON configuration used for storage, sync and export. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    encodeDefaults = true
    coerceInputValues = true
}

/** Pretty variant for human-readable export files. */
val PrettyJson: Json = Json(AppJson) { prettyPrint = true }

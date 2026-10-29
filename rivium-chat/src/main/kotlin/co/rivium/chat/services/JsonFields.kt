package co.rivium.chat.services

import org.json.JSONObject

/*
 * Reading optional fields from the API's JSON.
 *
 * On Android, `optString(key, null)` answers the text "null" for a JSON null
 * (`"editedAt": null`), and `optString(key)` answers "null" too. These helpers
 * read the raw value with `opt` and treat a missing key and a JSON null alike.
 */

/** The field as text, or null when it is missing or JSON null. */
internal fun JSONObject.stringOrNull(key: String): String? {
    if (isNull(key)) return null
    return opt(key)?.toString()
}

/** The field as text, or [default] when it is missing or JSON null. */
internal fun JSONObject.stringOr(key: String, default: String): String = stringOrNull(key) ?: default

/** The field as a whole number, or null when it is missing, JSON null or not a number. */
internal fun JSONObject.intOrNull(key: String): Int? {
    if (isNull(key)) return null
    return when (val value = opt(key)) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }
}

/** A JSON object of text values as a map; JSON null values are left out. */
internal fun JSONObject.stringMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    keys().forEach { key -> stringOrNull(key)?.let { map[key] = it } }
    return map
}

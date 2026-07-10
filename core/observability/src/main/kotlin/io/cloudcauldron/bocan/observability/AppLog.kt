package io.cloudcauldron.bocan.observability

import timber.log.Timber

/**
 * Structured logging facade for the whole app. Every module logs through this
 * interface, never through println or android.util.Log directly.
 *
 * Events are short dotted verbs ("sync.start", "pairing.failed"). Fields carry
 * the structured context. Values whose key matches [sensitiveKeys]
 * (case-insensitively) are replaced with "<redacted>" before emission.
 */
interface AppLog {
    fun debug(event: String, fields: Map<String, Any?> = emptyMap())

    fun info(event: String, fields: Map<String, Any?> = emptyMap())

    fun warning(event: String, fields: Map<String, Any?> = emptyMap())

    fun error(event: String, fields: Map<String, Any?> = emptyMap())

    companion object {
        val sensitiveKeys = setOf("token", "sessionKey", "password", "authorization", "apiKey", "code", "proof")

        fun forCategory(category: LogCategory): AppLog = TimberAppLog(category)
    }
}

/**
 * The Timber-backed implementation. The category renders as the Timber tag,
 * lowercased. The message is the event followed by space-separated key=value
 * pairs in the caller's field order.
 */
internal class TimberAppLog(private val category: LogCategory) : AppLog {
    override fun debug(event: String, fields: Map<String, Any?>) {
        tagged().d(render(event, fields))
    }

    override fun info(event: String, fields: Map<String, Any?>) {
        tagged().i(render(event, fields))
    }

    override fun warning(event: String, fields: Map<String, Any?>) {
        tagged().w(render(event, fields))
    }

    override fun error(event: String, fields: Map<String, Any?>) {
        tagged().e(render(event, fields))
    }

    private fun tagged(): Timber.Tree = Timber.tag(category.name.lowercase())

    private fun render(event: String, fields: Map<String, Any?>): String {
        if (fields.isEmpty()) return event
        val rendered = fields.entries.joinToString(separator = " ") { (key, value) ->
            val shown = if (isSensitive(key)) REDACTED else value.toString()
            "$key=$shown"
        }
        return "$event $rendered"
    }

    private fun isSensitive(key: String): Boolean = key.lowercase() in lowercasedSensitiveKeys

    private companion object {
        const val REDACTED = "<redacted>"
        val lowercasedSensitiveKeys = AppLog.sensitiveKeys.map(String::lowercase).toSet()
    }
}

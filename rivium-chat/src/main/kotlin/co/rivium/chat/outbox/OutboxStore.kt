package co.rivium.chat.outbox

import android.content.Context
import java.security.MessageDigest

/**
 * Where the outbox keeps messages written while offline.
 *
 * The default ([MemoryOutboxStore]) survives a lost network, not an app
 * restart. [SharedPreferencesOutboxStore] keeps them across restarts; pass
 * your own implementation for any other storage. Calls come from a
 * background thread.
 */
interface OutboxStore {
    /** Returns the saved messages in the order they were written. */
    fun load(): List<PendingMessage>

    /** Replaces the saved messages with [items]. */
    fun save(items: List<PendingMessage>)
}

/** Keeps the outbox in memory only (the default). */
class MemoryOutboxStore : OutboxStore {
    @Volatile
    private var items: List<PendingMessage> = emptyList()

    override fun load(): List<PendingMessage> = items

    override fun save(items: List<PendingMessage>) {
        this.items = items.toList()
    }
}

/**
 * Keeps the outbox in the app's SharedPreferences, so messages written
 * offline are still sent after an app restart.
 *
 * @param key one key per project and user; [keyFor] builds one.
 */
class SharedPreferencesOutboxStore(
    context: Context,
    private val key: String
) : OutboxStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): List<PendingMessage> {
        val text = try {
            prefs.getString(key, null)
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        return PendingMessage.listFromJson(text)
    }

    override fun save(items: List<PendingMessage>) {
        try {
            if (items.isEmpty()) {
                prefs.edit().remove(key).apply()
            } else {
                prefs.edit().putString(key, PendingMessage.listToJson(items)).apply()
            }
        } catch (e: Exception) {
            // Storage is best effort: the outbox keeps working in memory.
        }
    }

    companion object {
        private const val PREFS_NAME = "rivium_chat_outbox"

        /** A key for one project ([apiKey]) and one user ([userId]). */
        fun keyFor(apiKey: String, userId: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest("$apiKey\u0000$userId".toByteArray())
            return "outbox_" + digest.take(16).joinToString("") { "%02x".format(it) }
        }
    }
}

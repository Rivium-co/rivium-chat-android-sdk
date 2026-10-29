package co.rivium.chat.ui

import co.rivium.chat.models.Message
import co.rivium.chat.ui.components.isQueuedOffline
import co.rivium.chat.ui.state.markQueuedFailed
import co.rivium.chat.ui.state.mergeLiveMessage
import co.rivium.chat.ui.state.mergeSentMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A message queued offline is shown once: waiting, then replaced, never twice. */
class QueuedMessageMergeTest {

    private fun queued(cmid: String, content: String = "Order #1001 is ready") = Message(
        id = "local_$cmid", roomId = "room-1", senderUserId = "user-42", content = content,
        createdAt = "2026-10-29T10:00:00Z", isPending = true, clientMessageId = cmid
    )

    private fun stored(id: String, cmid: String?, content: String = "Order #1001 is ready") = Message(
        id = id, roomId = "room-1", senderUserId = "user-42", content = content,
        createdAt = "2026-10-29T10:00:01Z", clientMessageId = cmid
    )

    @Test
    fun `a queued message shows as waiting for network, not failed`() {
        val m = queued("abc")
        assertTrue(m.isQueuedOffline())
        assertFalse(m.isFailed)
        assertFalse(m.copy(id = "pending_1").isQueuedOffline())
    }

    @Test
    fun `messageSent replaces the waiting copy`() {
        val list = mutableListOf(queued("abc"))
        mergeSentMessage(list, "local_abc", stored("m-1", "abc"))
        assertEquals(listOf("m-1"), list.map { it.id })
        // then the live event: updated in place
        mergeLiveMessage(list, stored("m-1", "abc"))
        assertEquals(listOf("m-1"), list.map { it.id })
    }

    @Test
    fun `live event first, then messageSent - shown once`() {
        val list = mutableListOf(stored("m-0", null, "older"), queued("abc"))
        mergeLiveMessage(list, stored("m-1", "abc"))
        assertEquals(listOf("m-0", "m-1"), list.map { it.id })
        mergeSentMessage(list, "local_abc", stored("m-1", "abc"))
        assertEquals(listOf("m-0", "m-1"), list.map { it.id })
    }

    @Test
    fun `a queued copy with the same text as another message is not taken by it`() {
        val list = mutableListOf(queued("abc", "ok"))
        mergeLiveMessage(list, stored("m-5", null, "ok")) // someone else's / another send
        assertEquals(listOf("m-5", "local_abc"), list.map { it.id })
    }

    @Test
    fun `messageFailed marks the copy failed so Retry shows`() {
        val list = mutableListOf(queued("abc"))
        markQueuedFailed(list, "local_abc")
        assertTrue(list.single().isFailed)
        assertFalse(list.single().isPending)
    }
}

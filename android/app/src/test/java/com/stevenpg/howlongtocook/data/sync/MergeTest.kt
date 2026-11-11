package com.stevenpg.howlongtocook.data.sync

import com.stevenpg.howlongtocook.testing.product
import com.stevenpg.howlongtocook.testing.uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeTest {
    @Test
    fun `newer timestamp wins and ties go to remote`() {
        val local = product("a", updatedAt = 10, name = "local")
        val remote = product("a", updatedAt = 5, name = "remote")
        assertEquals(local, Merge.newer(local, remote))
        assertEquals(remote, Merge.newer(local.copy(updatedAt = 5), remote))
        assertEquals(remote, Merge.newer(null, remote))
        assertEquals(local, Merge.newer(local, null))
    }

    @Test
    fun `merge unions both sides`() {
        val r = Merge.merge(
            local = listOf(product("a", 2, "A new"), product("b", 1, "B")),
            remote = listOf(product("a", 1, "A old"), product("c", 1, "C")),
        )
        assertEquals(listOf("A new", "B", "C"), r.merged.map { it.name })
        assertEquals(listOf(uuid("c")), r.toLocal.map { it.id })
        assertTrue(r.remoteChanged)
    }

    @Test
    fun `identical sides need no writes`() {
        val items = listOf(product("a", 1, "A"), product("b", 1, "B"))
        val r = Merge.merge(items, items.reversed())
        assertTrue(r.toLocal.isEmpty())
        assertFalse(r.remoteChanged)
    }

    @Test
    fun `tombstones propagate`() {
        val r = Merge.merge(
            local = listOf(product("a", 1, "A")),
            remote = listOf(product("a", 2, "A").copy(deleted = true)),
        )
        assertTrue(r.toLocal.single().deleted)
        assertFalse(r.remoteChanged)
    }
}

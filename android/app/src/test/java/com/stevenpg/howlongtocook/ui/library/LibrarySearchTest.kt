package com.stevenpg.howlongtocook.ui.library

import com.stevenpg.howlongtocook.testing.product
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {
    private val pie = product("a", 1, "Chicken Pot Pie", barcode = "012345678905")
        .copy(brand = "Example Foods", tags = listOf("Dinner"), favorite = true)

    @Test
    fun `all terms must match some field`() {
        assertTrue(pie.matches(""))
        assertTrue(pie.matches("pot pie"))
        assertTrue(pie.matches("example dinner"))
        assertTrue(pie.matches("0123"))
        assertFalse(pie.matches("pot lasagna"))
    }

    @Test
    fun filters() {
        assertTrue(pie.matches(LibraryFilter.Favorites))
        assertTrue(pie.matches(LibraryFilter.Tag("dinner")))
        assertFalse(pie.matches(LibraryFilter.Tag("lunch")))
        assertFalse(pie.copy(favorite = false).matches(LibraryFilter.Favorites))
    }
}

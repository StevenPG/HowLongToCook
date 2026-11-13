package com.stevenpg.howlongtocook.data.model

import com.stevenpg.howlongtocook.testing.product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductValidationTest {
    @Test
    fun `normalizes like the server`() {
        val (p, errors) = ProductValidation.normalize(
            product("a", 1).copy(
                name = "  Pot Pie ",
                brand = "  ",
                barcodes = listOf("123456", " 123456 ", ""),
                tags = listOf("Dinner", "dinner", " "),
            ),
        )
        assertTrue(errors.isEmpty())
        assertEquals("Pot Pie", p.name)
        assertNull(p.brand)
        assertEquals(listOf("123456"), p.barcodes)
        assertEquals(listOf("Dinner"), p.tags)
    }

    @Test
    fun `reports problems`() {
        val (_, issues) = ProductValidation.normalize(
            product("a", 1).copy(name = " ", barcodes = listOf("no way!"), rating = 7),
        )
        assertEquals(
            listOf(
                ValidationIssue(ValidationIssue.Type.NAME_REQUIRED),
                ValidationIssue(ValidationIssue.Type.INVALID_BARCODE, value = "no way!"),
                ValidationIssue(ValidationIssue.Type.RATING_OUT_OF_RANGE),
            ),
            issues,
        )
    }
}

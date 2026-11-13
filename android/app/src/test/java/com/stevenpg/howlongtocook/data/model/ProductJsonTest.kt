package com.stevenpg.howlongtocook.data.model

import com.stevenpg.howlongtocook.testing.sharedExample
import com.stevenpg.howlongtocook.util.AppJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the JSON contract shared with the Rust server (api/examples/product.json). */
class ProductJsonTest {
    private val text = sharedExample("product.json")

    @Test
    fun `decodes the shared example`() {
        val p = AppJson.decodeFromString(Product.serializer(), text)
        assertEquals("Chicken Pot Pie", p.name)
        assertEquals(MethodType.MICROWAVE, p.preferredMethod?.type)
        assertEquals(420, p.methods[0].totalSeconds)
        assertEquals(Temperature(400, TemperatureUnit.F), p.methods[1].temperature)
        assertEquals(StepAction.VENT, p.methods[0].steps[0].action)
    }

    @Test
    fun `re-encodes to the identical JSON tree`() {
        val p = AppJson.decodeFromString(Product.serializer(), text)
        val encoded = AppJson.encodeToString(Product.serializer(), p)
        assertEquals(Json.parseToJsonElement(text), Json.parseToJsonElement(encoded) as JsonElement)
    }

    @Test
    fun `tolerates unknown fields and missing optionals`() {
        val minimal = """{"id":"x","name":"Soup","createdAt":1,"updatedAt":2,"future":true}"""
        val p = AppJson.decodeFromString(Product.serializer(), minimal)
        assertEquals(emptyList<CookingMethod>(), p.methods)
        assertEquals(false, p.deleted)
    }
}

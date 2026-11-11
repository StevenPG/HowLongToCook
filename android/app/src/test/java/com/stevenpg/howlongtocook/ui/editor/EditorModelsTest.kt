package com.stevenpg.howlongtocook.ui.editor

import com.stevenpg.howlongtocook.data.model.CookingMethod
import com.stevenpg.howlongtocook.data.model.MethodType
import com.stevenpg.howlongtocook.data.model.Step
import com.stevenpg.howlongtocook.data.model.StepAction
import com.stevenpg.howlongtocook.data.model.Temperature
import com.stevenpg.howlongtocook.data.model.TemperatureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorModelsTest {
    @Test
    fun `method round-trips through the draft`() {
        val method = CookingMethod(
            type = MethodType.OVEN,
            temperature = Temperature(425, TemperatureUnit.F),
            preheat = true,
            steps = listOf(Step(StepAction.COOK, 1500, "Center rack"), Step(StepAction.STAND, 90)),
            notes = "Crispy",
        )
        val errors = mutableListOf<String>()
        assertEquals(method, method.toDraft(TemperatureUnit.C).toMethod(0, errors))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `invalid input is reported`() {
        val draft = MethodDraft(
            type = MethodType.OVEN,
            temperature = "hot",
            steps = listOf(StepDraft(minutes = "3", seconds = "75")),
        )
        val errors = mutableListOf<String>()
        draft.toMethod(1, errors)
        assertEquals(2, errors.size)
        assertTrue(errors.all { it.startsWith("Method 2") })
    }

    @Test
    fun `blank cook rows are dropped and power is ignored for ovens`() {
        val draft = MethodDraft(
            type = MethodType.OVEN,
            power = "High",
            steps = listOf(StepDraft(minutes = "20"), StepDraft()),
        )
        val m = draft.toMethod(0, mutableListOf())
        assertEquals(1, m.steps.size)
        assertEquals(null, m.power)
    }

    @Test
    fun `defaults follow the unit`() {
        assertEquals("200", defaultMethod(MethodType.OVEN, TemperatureUnit.C).temperature)
        assertEquals("400", defaultMethod(MethodType.OVEN, TemperatureUnit.F).temperature)
        assertEquals(3, defaultMethod(MethodType.AIR_FRYER, TemperatureUnit.F).steps.size)
    }
}

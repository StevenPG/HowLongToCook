package com.stevenpg.howlongtocook.util

import com.stevenpg.howlongtocook.data.model.CookingMethod
import com.stevenpg.howlongtocook.data.model.MethodType
import com.stevenpg.howlongtocook.data.model.Step
import com.stevenpg.howlongtocook.data.model.StepAction
import com.stevenpg.howlongtocook.data.model.Temperature
import com.stevenpg.howlongtocook.data.model.TemperatureUnit
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FormattersTest {
    private val res: Resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    @Test
    fun temperatures() {
        assertEquals("400°F", Temperature(400, TemperatureUnit.F).displayIn(TemperatureUnit.F))
        assertEquals("200°C", Temperature(400, TemperatureUnit.F).displayIn(TemperatureUnit.C))
        assertEquals("190°C", Temperature(375, TemperatureUnit.F).displayIn(TemperatureUnit.C))
        assertEquals("425°F", Temperature(220, TemperatureUnit.C).displayIn(TemperatureUnit.F))
        assertEquals("350°F", Temperature(180, TemperatureUnit.C).displayIn(TemperatureUnit.F))
        assertEquals("183°C", Temperature(183, TemperatureUnit.C).displayIn(TemperatureUnit.C))
    }

    @Test
    fun locales() {
        assertEquals(TemperatureUnit.F, defaultUnitFor(Locale.US))
        assertEquals(TemperatureUnit.C, defaultUnitFor(Locale.UK))
        assertEquals(TemperatureUnit.C, defaultUnitFor(Locale.GERMANY))
    }

    @Test
    fun durations() {
        assertEquals("45 sec", formatDuration(res, 45))
        assertEquals("7 min", formatDuration(res, 420))
        assertEquals("1:30", formatDuration(res, 90))
        assertEquals("1 hr", formatDuration(res, 3600))
        assertEquals("1 hr 15 min", formatDuration(res, 4500))
        assertEquals("4:00", formatClock(240))
        assertEquals("1:05:00", formatClock(3900))
    }

    @Test
    fun clockParsing() {
        assertEquals(240, parseClock("4"))
        assertEquals(270, parseClock("4:30"))
        assertEquals(3870, parseClock("1:04:30"))
        assertNull(parseClock("4:75"))
        assertNull(parseClock("abc"))
        assertNull(parseClock(""))
    }

    @Test
    fun summaries() {
        val m = CookingMethod(MethodType.MICROWAVE, steps = listOf(Step(StepAction.COOK, 240), Step(StepAction.STIR), Step(StepAction.COOK, 180)))
        assertEquals("Microwave · 7 min", m.summary(res))
        assertEquals("No instructions yet", (null as CookingMethod?).summary(res))
        assertEquals("Stir", Step(StepAction.STIR).title(res))
        assertEquals("Cook 4:00", Step(StepAction.COOK, 240).title(res))
        assertEquals("Remove film 1:00", Step(StepAction.OTHER, 60, "Remove film").title(res))
    }
}

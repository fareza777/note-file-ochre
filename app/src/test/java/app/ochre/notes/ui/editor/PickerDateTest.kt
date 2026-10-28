package app.ochre.notes.ui.editor

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PickerDateTest {
    @Test
    fun `picker millis is utc midnight of the chosen day`() {
        assertEquals(1_793_059_200_000L, pickerDateMillis(LocalDate.of(2026, 10, 27)))
    }

    @Test
    fun `picker millis round trips regardless of device zone`() {
        val date = LocalDate.of(2026, 3, 1)
        assertEquals(date, dateFromPickerMillis(pickerDateMillis(date)))
    }

    @Test
    fun `date from picker ignores time of day`() {
        val millis = 1_793_059_200_000L + 86_399_999L
        assertEquals(LocalDate.of(2026, 10, 27), dateFromPickerMillis(millis))
    }
}
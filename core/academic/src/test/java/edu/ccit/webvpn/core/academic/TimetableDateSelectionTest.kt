package edu.ccit.webvpn.core.academic

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimetableDateSelectionTest {
    @Test
    fun deviceDate_usesTeachingWeekReturnedByAcademicHomePage() {
        val timetable = timetable(
            referenceDate = LocalDate.of(2026, 7, 11),
            referenceWeek = 18,
            totalWeeks = 25,
        )

        val selection = timetable.selectionForDate(LocalDate.of(2026, 7, 11))

        assertEquals(18, selection.week)
        assertEquals(6, selection.dayOfWeek)
        assertEquals(25, timetable.maxWeek)
    }

    @Test
    fun anotherWeekDate_isCalculatedFromServerAnchorRatherThanSemesterGuess() {
        val timetable = timetable(
            referenceDate = LocalDate.of(2026, 7, 11),
            referenceWeek = 18,
            totalWeeks = 25,
        )

        assertEquals(LocalDate.of(2026, 6, 29), timetable.dateFor(17, 1))
        assertEquals(20, timetable.selectionForDate(LocalDate.of(2026, 7, 20)).week)
    }

    @Test
    fun unavailableServerWeek_doesNotGuessSemesterStartDate() {
        val timetable = timetable()

        val selection = timetable.selectionForDate(LocalDate.of(2026, 7, 11))

        assertEquals(1, selection.week)
        assertEquals(6, selection.dayOfWeek)
        assertNull(timetable.dateFor(1, 6))
    }

    private fun timetable(
        referenceDate: LocalDate? = null,
        referenceWeek: Int? = null,
        totalWeeks: Int? = null,
    ) = AcademicTimetable(
        terms = emptyList(),
        selectedTerm = "2025-2026-2",
        periods = emptyList(),
        courses = emptyList(),
        note = "",
        schemeId = "scheme-1",
        referenceDate = referenceDate,
        referenceWeek = referenceWeek,
        totalWeeks = totalWeeks,
    )
}

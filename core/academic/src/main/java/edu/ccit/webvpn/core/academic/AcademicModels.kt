package edu.ccit.webvpn.core.academic

import java.time.LocalDate

data class AcademicTerm(
    val value: String,
    val label: String,
    val selected: Boolean = false,
)

data class CourseGrade(
    val sequence: String,
    val semester: String,
    val courseCode: String,
    val courseName: String,
    val groupName: String,
    val score: String,
    val scoreMark: String,
    val credit: String,
    val totalHours: String,
    val gradePoint: String,
    val generalElective: String,
    val originalScore: String,
    val description: String,
    val note: String,
    val retakeSemester: String,
    val assessmentMethod: String,
    val examType: String,
    val courseAttribute: String,
    val courseNature: String,
    val courseCategory: String,
)

data class AcademicOverview(
    val terms: List<AcademicTerm>,
) {
    val defaultTerm: String
        get() = terms.firstOrNull(AcademicTerm::selected)?.value
            ?: terms.firstOrNull { it.value.isNotBlank() }?.value.orEmpty()
}

data class TimetablePeriod(
    val index: Int,
    val label: String,
    val startTime: String,
    val endTime: String,
)

data class TimetableCourse(
    val id: String,
    val dayOfWeek: Int,
    val periodIndex: Int,
    val startSection: Int,
    val endSection: Int,
    val name: String,
    val teacher: String,
    val weeks: String,
    val weekNumbers: Set<Int>,
    val location: String,
)

data class AcademicTimetable(
    val terms: List<AcademicTerm>,
    val selectedTerm: String,
    val periods: List<TimetablePeriod>,
    val courses: List<TimetableCourse>,
    val note: String,
    val schemeId: String,
    val referenceDate: LocalDate? = null,
    val referenceWeek: Int? = null,
    val totalWeeks: Int? = null,
) {
    val maxWeek: Int
        get() = maxOf(
            20,
            totalWeeks ?: 0,
            courses.flatMap(TimetableCourse::weekNumbers).maxOrNull() ?: 0,
        )

    /**
     * Maps a device-local date from the teaching-week value returned by the
     * academic home page. No semester-start date is guessed locally.
     */
    fun selectionForDate(date: LocalDate): TimetableDateSelection {
        val anchorDate = referenceDate
        val anchorWeek = referenceWeek
        val rawWeek = if (anchorDate != null && anchorWeek != null) {
            val anchorMonday = anchorDate.minusDays(anchorDate.dayOfWeek.value - 1L)
            val dateMonday = date.minusDays(date.dayOfWeek.value - 1L)
            anchorWeek + ((dateMonday.toEpochDay() - anchorMonday.toEpochDay()) / 7L).toInt()
        } else {
            1
        }
        val week = rawWeek.coerceIn(1, maxWeek)
        val day = date.dayOfWeek.value
        return TimetableDateSelection(week, day, dateFor(week, day) ?: date)
    }

    fun dateFor(week: Int, dayOfWeek: Int): LocalDate? {
        val anchorDate = referenceDate ?: return null
        val anchorWeek = referenceWeek ?: return null
        val anchorMonday = anchorDate.minusDays(anchorDate.dayOfWeek.value - 1L)
        return anchorMonday.plusDays(
            (week.coerceAtLeast(1) - anchorWeek) * 7L + (dayOfWeek.coerceIn(1, 7) - 1L),
        )
    }
}

data class TimetableDateSelection(
    val week: Int,
    val dayOfWeek: Int,
    val date: LocalDate,
)

class AcademicApiException(
    override val message: String,
    val loginRequired: Boolean = false,
) : Exception(message)

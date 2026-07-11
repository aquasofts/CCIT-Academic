package edu.ccit.webvpn.core.academic

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicHtmlParserTest {
    @Test
    fun parseTerms_preservesSelectedSemester() {
        val html = """
            <select id="kksj" name="kksj">
              <option value="">全部学期</option>
              <option value="2025-2026-2" selected="selected">2025-2026-2</option>
              <option value="2025-2026-1">2025-2026-1</option>
            </select>
        """.trimIndent()

        val terms = AcademicHtmlParser.parseTerms(html)

        assertEquals(3, terms.size)
        assertEquals("2025-2026-2", terms.single(AcademicTerm::selected).value)
    }

    @Test
    fun parseGrades_mapsAllTwentyColumns() {
        val values = listOf(
            "1", "2025-2026-2", "COURSE-1", "高等数学", "", "95", "", "4", "64", "4.5",
            "否", "95", "", "", "", "考试", "正常考试", "必修", "公共基础课", "理论课",
        )
        val html = "<table id='dataList'><tr>" +
            values.joinToString("") { "<td>$it</td>" } +
            "</tr></table>"

        val grade = AcademicHtmlParser.parseGrades(html).single()

        assertEquals("高等数学", grade.courseName)
        assertEquals("95", grade.score)
        assertEquals("4.5", grade.gradePoint)
        assertEquals("理论课", grade.courseCategory)
    }

    @Test
    fun encodeCredentials_matchesObservedBase64SeparatorFormat() {
        val encoded = AcademicRepository.encodeCredentials("student", "password")
        val parts = encoded.split("%%%")

        assertEquals(2, parts.size)
        assertEquals("student", String(Base64.getDecoder().decode(parts[0])))
        assertEquals("password", String(Base64.getDecoder().decode(parts[1])))
        assertFalse(encoded.contains("student"))
        assertFalse(encoded.contains("password"))
    }

    @Test
    fun detectsStudentLoginPage() {
        assertTrue(AcademicHtmlParser.isStudentLoginPage("<form action='/jsxsd/xk/LoginToXk'><input name='RANDOMCODE'><input name='userAccount'></form>"))
        assertFalse(AcademicHtmlParser.isStudentLoginPage("<title>课程成绩查询</title>"))
    }

    @Test
    fun parseTimetable_mapsTermsPeriodsAndMultipleCourses() {
        val html = """
            <select name="xnxq01id">
              <option value="2026-2027-1">2026-2027-1</option>
              <option value="2025-2026-2" selected="selected">2025-2026-2</option>
            </select>
            <input name="kbjcmsid" value="scheme-1">
            <table id="kbtable">
              <tr><td>&nbsp;</td><td>星期一</td><td>星期二</td><td>星期三</td><td>星期四</td><td>星期五</td><td>星期六</td><td>星期日</td></tr>
              <tr>
                <td>第一大节<br>08:00-09:35</td>
                <td><div class="kbcontent1">简略</div><div class="kbcontent">大学物理Ⅳ<br><font title="老师">高金宇</font><br><font title="周次(节次)">1,4-14(周)[01-02节]</font><br><font title="教室">DS2-101</font><br>------<br>职业规划<br><font title="老师">刘老师</font><br><font title="周次(节次)">16(周)[01-02节]</font><br><font title="教室">A205</font></div></td>
                <td></td><td></td><td></td><td></td><td></td><td></td>
              </tr>
              <tr><td colspan="8">备注：线上课程</td></tr>
            </table>
        """.trimIndent()

        val timetable = requireNotNull(AcademicHtmlParser.parseTimetable(html))

        assertEquals("2025-2026-2", timetable.selectedTerm)
        assertEquals("scheme-1", timetable.schemeId)
        assertEquals("08:00", timetable.periods.single().startTime)
        assertEquals(2, timetable.courses.size)
        assertEquals("高金宇", timetable.courses.first().teacher)
        assertTrue(14 in timetable.courses.first().weekNumbers)
        assertFalse(3 in timetable.courses.first().weekNumbers)
        assertEquals("线上课程", timetable.note)
    }
}

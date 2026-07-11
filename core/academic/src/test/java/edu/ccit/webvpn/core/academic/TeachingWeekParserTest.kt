package edu.ccit.webvpn.core.academic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeachingWeekParserTest {
    @Test
    fun parsesWeekElementFromAcademicHomePageResponse() {
        val html = """
            <script type="text/javascript">
            ${'$'}("#li_showWeek").html("<span class=\"main_text main_color\">第18周</span>/25周");
            </script>
        """.trimIndent()

        val result = AcademicHtmlParser.parseTeachingWeek(html)

        assertEquals(18, result?.week)
        assertEquals(25, result?.totalWeeks)
    }

    @Test
    fun missingWeekElement_returnsNullInsteadOfGuessing() {
        assertNull(AcademicHtmlParser.parseTeachingWeek("<table></table>"))
    }
}

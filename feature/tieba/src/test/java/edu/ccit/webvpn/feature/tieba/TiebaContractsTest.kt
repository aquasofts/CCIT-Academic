package edu.ccit.webvpn.feature.tieba

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TiebaContractsTest {
    @Test
    fun onlyTargetForumUsesNativeRoute() {
        assertEquals(ForumRouteDecision.Native, forumRouteDecision("长春工程学院吧"))
        assertTrue(forumRouteDecision("其他吧") is ForumRouteDecision.External)
    }

    @Test
    fun imageSelectionAlwaysReturnsOriginalHttpsUrl() {
        assertEquals(
            "https://imgsa.baidu.com/forum/pic/item/a.jpg",
            originalImageUrl("http://imgsa.baidu.com/forum/pic/item/a.jpg?tbpicau=1"),
        )
    }

    @Test
    fun automaticSignRunsOnlyOnTheFirstForegroundOfEachLocalDay() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = Instant.parse("2026-07-16T08:00:00Z")
        val sameLocalDay = Instant.parse("2026-07-15T18:00:00Z").toEpochMilli()
        val previousLocalDay = Instant.parse("2026-07-15T12:00:00Z").toEpochMilli()

        assertTrue(shouldAutoSign(null, now, zone))
        assertFalse(shouldAutoSign(sameLocalDay, now, zone))
        assertTrue(shouldAutoSign(previousLocalDay, now, zone))
    }
}

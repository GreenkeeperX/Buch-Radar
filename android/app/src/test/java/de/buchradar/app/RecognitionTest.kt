package de.buchradar.app

import de.buchradar.app.scanner.*
import org.junit.Assert.*
import org.junit.Test

class RecognitionTest {
    @Test fun steadyButUnreadableDoesNotTrigger() {
        val rule = RecognitionStability()
        repeat(20) { assertFalse(rule.observe("Victor Hugo Notre Dame", it * 500L, true, false)) }
    }
    @Test fun readableStableTextTriggersAfterDwell() {
        val rule = RecognitionStability()
        repeat(5) { assertFalse(rule.observe("Victor Hugo Notre Dame", it * 500L, true, true)) }
        assertTrue(rule.observe("Victor Hugo Notre Dame", 2500L, true, true))
    }
    @Test fun changingSceneResetsDwell() {
        val rule = RecognitionStability()
        repeat(5) { rule.observe("Victor Hugo Notre Dame", it * 500L, true, true) }
        assertFalse(rule.observe("Thomas Mann Zauberberg", 2500, true, true))
    }
    @Test fun adjacentSpinesStaySeparateAndIdenticalNamesAreNotDeduplicated() {
        val pieces = listOf(
            OcrPiece("Hugo", 10f, 10f, 30f, 50f, 90f),
            OcrPiece("Notre Dame", 10f, 60f, 30f, 150f, 90f),
            OcrPiece("Hugo", 40f, 10f, 60f, 50f, 90f),
            OcrPiece("Les Misérables", 40f, 60f, 60f, 150f, 90f))
        val groups = groupSpines(pieces, 100, 300)
        assertEquals(2, groups.size)
        assertTrue(groups.all { group -> group.any { it.text == "Hugo" } })
    }
}

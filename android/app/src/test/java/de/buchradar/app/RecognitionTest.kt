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
    @Test fun shelfToleranceAcceptsSmallOcrVariation() {
        val strict = RecognitionStability()
        val shelf = RecognitionStability().apply { minSimilarity = .70f }
        val frames = listOf("Hugo Notre Dame Mann Zauberberg Fontane", "Hugo Notre Dame Mann Zauberberg Fontano")
        var strictReady = false; var shelfReady = false
        repeat(6) { i ->
            strictReady = strict.observe(frames[i % 2], i * 500L, true, true) || strictReady
            shelfReady = shelf.observe(frames[i % 2], i * 500L, true, true) || shelfReady
        }
        assertFalse(strictReady)
        assertTrue(shelfReady)
    }
    @Test fun brightnessFlickerIsNotMovement() {
        val base = IntArray(4096) { (it * 37) % 200 }
        val brighter = IntArray(4096) { base[it] + 20 }
        val shifted = IntArray(4096) { base[(it + 1) % 4096] }
        assertTrue(frameDifference(base, brighter) < 1.0)
        assertTrue(frameDifference(base, shifted) > 12.0)
    }
    @Test fun publisherAndFragmentGroupsAreNotBooks() {
        assertFalse(isUsefulSpineText("Hanser"))
        assertFalse(isUsefulSpineText("dty !"))
        assertFalse(isUsefulSpineText("BROCKHAUS LEIPZIG"))
        assertTrue(isUsefulSpineText("Tom Reiss"))
        assertTrue(isUsefulSpineText("DER ORIENTALIST\nHanser"))
        assertTrue(isUsefulSpineText("Hofstadter Gödel, Escher, Bach"))
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

    @Test fun distantVerticalAuthorAndTitleShareOnlyTheirOwnColumn() {
        val author = OcrPiece("Tom Reiss", 20f, 10f, 40f, 65f, -90f)
        val title = OcrPiece("Der Orientalist", 23f, 340f, 39f, 430f, 270f)
        val neighbour = title.copy(text = "Nachbar", left = 36f, right = 54f)
        val groups = groupSpines(listOf(title, neighbour, author), 400, 450)
        assertEquals(listOf(setOf(author, title), setOf(neighbour)), groups.map { it.toSet() })
    }

    @Test fun aDistantHorizontalOrMuchWiderBoxCannotBridgeTwoBooks() {
        val title = OcrPiece("Titel", 20f, 340f, 40f, 430f, 90f)
        val author = title.copy(text = "Autor", top = 10f, bottom = 60f)
        assertEquals(2, groupSpines(listOf(title, author.copy(angle = 0f)), 400, 450).size)
        assertEquals(2, groupSpines(listOf(title, author.copy(right = 90f)), 400, 450).size)
    }

    @Test fun spineStutterIsRemovedButDistinctLinesStay() {
        assertEquals("NANSEN\nIN NACHT UND EIS", cleanSpineText("NANSEN NANSEN\nIN NACHT UND EIS"))
        assertEquals("Transhimalaja", cleanSpineText("Transhimalaa Transhimalaja"))
        assertEquals("Sven Hedin\nTranshimalaja", cleanSpineText("Sven Hedin\nHedin\nTranshimalaja"))
        assertEquals("Georg Christoph Lichtenberg\nWagnis der Aufklärung",
            cleanSpineText("Georg Christoph Lichtenberg\nWagnis der Aufklärung"))
        assertEquals("Band 1\nBand 2", cleanSpineText("Band 1\nBand 2"))
        assertEquals("Band 1\nBand 12", cleanSpineText("Band 1\nBand 12"))
        assertEquals("IT\nIllustrated Edition", cleanSpineText("IT\nIllustrated Edition"))
        assertEquals("ISBN 3125171547 3123171547", cleanSpineText("ISBN 3125171547 3123171547"))
    }
}

package de.buchradar.app

import de.buchradar.app.scanner.OcrPiece
import de.buchradar.app.scanner.ShelfRegionText
import de.buchradar.app.scanner.SpineEdge
import de.buchradar.app.scanner.SpineRegion
import de.buchradar.app.scanner.readableSpine
import de.buchradar.app.scanner.shelfReadings
import de.buchradar.app.scanner.spineBand
import de.buchradar.app.scanner.spineEdges
import de.buchradar.app.scanner.spineRegions
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class SpineSegmentationTest {
    private fun edge(x: Int, slope: Float = 0f) = SpineEdge(x.toFloat(), slope, 60f)

    @Test fun longBookBoundariesSurviveShortTextEdges() {
        val width = 320; val height = 240
        val colors = intArrayOf(0xffeeeeee.toInt(), 0xff226644.toInt(), 0xffddddbb.toInt(), 0xff772222.toInt())
        val pixels = IntArray(width * height) { index ->
            val x = index % width; val y = index / width
            if (x in 24..29 && y in 80..110) 0xff000000.toInt() else colors[x / 80]
        }
        val edges = spineEdges(pixels, width, height)
        assertEquals(3, edges.size)
        listOf(80, 160, 240).forEach { expected -> assertTrue(edges.any { abs(it.x - expected) <= 3 }) }
    }

    @Test fun slantedBoundaryRetainsDirectionForRectification() {
        val width = 320; val height = 240
        val pixels = IntArray(width * height) { i ->
            if (i % width < 160 + .12f * (i / width - height * .55f)) 0xffeeeeee.toInt() else 0xff223344.toInt()
        }
        val edge = spineEdges(pixels, width, height).maxBy { it.strength }
        assertTrue(abs(edge.x - 160) < 3)
        assertTrue(edge.slope > 0)
        assertTrue(edge.at(0f, height) < edge.at(height.toFloat(), height))
    }

    @Test fun edgeIsFoundWhenItsDirectionChangesAlongTheSpine() {
        // The left spine is light at the top, matches its grey neighbour in the middle and is dark
        // at the bottom: the colour step changes sign along the boundary, so a signed median is zero.
        val width = 320; val height = 240
        val pixels = IntArray(width * height) { index ->
            val x = index % width; val y = index / width
            when {
                x >= 160 -> 0xff808080.toInt()
                y < height * .45f -> 0xffd8d8d8.toInt()
                y < height * .65f -> 0xff808080.toInt()
                else -> 0xff282828.toInt()
            }
        }
        assertTrue(spineEdges(pixels, width, height).any { abs(it.x - 160) <= 3 })
    }

    @Test fun flatImageDoesNotInventBooks() {
        assertTrue(spineEdges(IntArray(320 * 240) { 0xff334455.toInt() }, 320, 240).isEmpty())
        assertTrue(spineEdges(IntArray(3), 320, 240).isEmpty())
    }

    @Test fun booksInLowerHalfAreFoundWithinTheLetteringBand() {
        // Books only in the lower 35 % (shelf board and wall above): the fixed band misses them.
        val width = 320; val height = 400
        val colors = intArrayOf(0xffeeeeee.toInt(), 0xff226644.toInt(), 0xffddddbb.toInt(), 0xff772222.toInt())
        val pixels = IntArray(width * height) { index ->
            val x = index % width; val y = index / width
            if (y < 260) 0xff808080.toInt() else colors[x / 80]
        }
        assertTrue(spineEdges(pixels, width, height).size < 3)
        val lettering = listOf(
            OcrPiece("Hestia", 30f, 280f, 50f, 380f, 90f),
            OcrPiece("Der Feind", 110f, 275f, 130f, 385f, 270f))
        val (top, bottom) = spineBand(lettering, height)
        assertTrue(top > .5f && bottom < 1f)
        val edges = spineEdges(pixels, width, height, top, bottom)
        listOf(80, 160, 240).forEach { expected -> assertTrue(edges.any { abs(it.x - expected) <= 3 }) }
    }

    @Test fun bandFallsBackWithoutVerticalLettering() {
        assertEquals(.23f to .88f, spineBand(emptyList(), 400))
        assertEquals(.23f to .88f, spineBand(listOf(OcrPiece("Titel", 0f, 10f, 100f, 30f, 0f)), 400))
    }

    @Test fun spineWithOnlyAnIsbnIsKept() {
        assertTrue(readableSpine("978-3-12-517154-1"))
        assertTrue(readableSpine("Der Orientalist"))
        assertFalse(readableSpine("12 34"))
    }

    @Test fun broadVerticalLineVetoesOnlyTheEdgeThroughItsCoreAtTheTextHeight() {
        val edges = listOf(edge(100), edge(180, -.22f), edge(244), edge(420), edge(600), edge(800))
        val lines = listOf(
            OcrPiece("Durchgehende Zeile", 174f, 20f, 246f, 132f, 90f),
            OcrPiece("Quer über zwei Bücher", 350f, 70f, 500f, 95f, 0f))
        val regions = spineRegions(edges, lines, 1000, 400)
        assertEquals(listOf(100f, 244f, 420f, 600f, 800f, 1000f), regions.map { it.right.x })
    }

    @Test fun separateTextColumnsDivideEvenAWideFeaturelessRegion() {
        val author = OcrPiece("Autor", 210f, 30f, 232f, 140f, -90f)
        val title = author.copy(text = "Titel", left = 215f, right = 235f, top = 180f, bottom = 360f)
        val neighbour = author.copy(left = 735f, right = 757f)
        val lines = listOf(title, neighbour, author, author.copy(angle = 270f),
            author.copy(text = "I", left = 455f, right = 460f),
            OcrPiece("quer gedruckt", 190f, 10f, 770f, 30f, 0f))
        val regions = spineRegions(emptyList(), lines, 1000, 400)
        assertEquals(2, regions.size)
        assertEquals(485f, regions.first().right.x, .01f)
        assertEquals(regions.first().right, regions.last().left)
        assertEquals(0f, regions.first().left.x, 0f)
        assertEquals(1000f, regions.last().right.x, 0f)
    }

    @Test fun closeOrOverlappingBoxesCannotSplitABook() {
        val line = OcrPiece("Autor", 250f, 40f, 275f, 180f, -90f)
        val lines = listOf(line, line.copy(left = 230f, right = 330f),
            line.copy(text = "Titel", left = 335f, right = 360f))
        val edges = listOf(200, 400, 600, 800).map { edge(it) }
        assertEquals(listOf(200f, 400f, 600f, 800f, 1000f),
            spineRegions(edges, lines, 1000, 400).map { it.right.x })
        assertTrue(spineRegions(emptyList(), lines, 1000, 400).isEmpty())
    }

    @Test fun missingCutFollowsItsSlopingNeighboursAndAvoidsBothColumns() {
        val edges = listOf(edge(120, .16f), edge(360, .24f), edge(550), edge(745))
        val a = OcrPiece("Alpha", 152f, 110f, 172f, 180f, 80f)
        val b = a.copy(text = "Beta", left = 294f, right = 314f)
        val regions = spineRegions(edges, listOf(a, b), 1000, 400)
        val cut = regions.single { it.left == edges.first() }.right
        assertTrue(cut.slope > .16f && cut.slope < .24f)
        for (y in listOf(a.top, a.bottom)) assertTrue(cut.at(y, 400) in a.right..b.left)
        assertEquals(cut, regions.single { it.right == edges[1] }.left)
    }

    @Test fun marginOfLeaningLetteringOnANeighbourCannotRemoveOrAddAnEdge() {
        val edges = listOf(edge(200, .25f), edge(390, .25f), edge(590), edge(790))
        val neighbour = OcrPiece("Schräger Nachbar", 139f, 60f, 233f, 380f, 76f)
        val title = OcrPiece("Eigener Titel", 320f, 100f, 342f, 180f, 90f)
        val regions = spineRegions(edges, listOf(neighbour, title), 1000, 400)
        assertEquals(listOf(200f, 390f, 590f, 790f, 1000f), regions.map { it.right.x })
    }

    @Test fun onlyCorroboratedFragmentsAcrossAnAdjacentMeasuredEdgeAreJoined() {
        val regions = listOf(945, 979, 1000).map { edge(it) }.zipWithNext(::SpineRegion)
        val left = "BORGE\nEin ewiger Traum"
        val witness = OcrPiece("BORGES", 950f, 40f, 985f, 65f, 0f)
        fun readings(pieces: List<OcrPiece>, crops: List<SpineRegion> = regions, second: String = "ES") =
            shelfReadings(crops, mapOf(crops[0] to left, crops[1] to second), pieces, 1000, 400)
        assertEquals(listOf(ShelfRegionText("BORGES\nEin ewiger Traum", .945f, 1f)), readings(listOf(witness)))
        assertEquals(2, readings(listOf(witness.copy(left = 900f, right = 935f))).size)
        assertEquals(2, readings(listOf(witness.copy(text = "BORGE ES"))).size)
        assertEquals(2, readings(listOf(witness), second = "BORGE").size)
        assertEquals(2, readings(listOf(witness), listOf(regions[0], regions[1].copy(left = edge(983)))).size)
        assertEquals(2, readings(listOf(witness), listOf(regions[0].copy(left = edge(895)), regions[1])).size)
    }

    @Test fun unfinishedBatchUsesOnlyTheTextInsideEachRemainingCrop() {
        val regions = listOf(0, 200, 400).map { edge(it) }.zipWithNext(::SpineRegion)
        val piece = OcrPiece("Zweiter Titel", 255f, 30f, 272f, 190f, 90f)
        val readings = shelfReadings(regions, mapOf(regions[0] to "Erster Titel"),
            listOf(piece, piece.copy(text = "über die Grenze", left = 190f, right = 220f)), 400, 400)
        assertEquals(listOf("Erster Titel", "Zweiter Titel"), readings.map { it.text })
    }

    @Test fun aWideAreaWithNoColumnGutterKeepsItsGroupsAlongsideAtlasReadings() {
        val a = OcrPiece("Erster Band", 40f, 30f, 110f, 55f, 0f)
        val b = a.copy(text = "Zweiter Band", left = 230f, right = 300f)
        val edges = listOf(400, 600, 720, 840).map { edge(it) }
        val regions = spineRegions(edges, listOf(a, b), 1000, 400)
        assertTrue(regions.first().firstPassOnly)
        assertTrue(regions.drop(1).none { it.firstPassOnly })
        val readings = shelfReadings(regions, mapOf(regions[1] to "Dritter Band"), listOf(a, b), 1000, 400)
        assertEquals(listOf(ShelfRegionText("Erster Band", .04f, .11f), ShelfRegionText("Zweiter Band", .23f, .3f),
            ShelfRegionText("Dritter Band", .4f, .6f)), readings.take(3))
    }

    @Test fun steepBooksHaveOneEdgePerBoundaryAndRectifyToTheCorrectPixels() {
        val width = 480
        val height = 320
        val colors = intArrayOf(0xffeeeeee.toInt(), 0xff226644.toInt(), 0xffddddbb.toInt(), 0xff772222.toInt())
        for (slope in listOf(-.25f, -.205f, .205f, .25f)) {
            fun offset(y: Int) = slope * (y - height * .55f)
            val pixels = IntArray(width * height) { index ->
                val y = index / width
                val x = index % width - offset(y)
                if (x in 143f..148f && y in 40..90) 0xff000000.toInt()
                else colors[(x / 120).toInt().coerceIn(colors.indices)]
            }
            val edges = spineEdges(pixels, width, height)
            assertEquals("slope $slope", 3, edges.size)
            for ((i, e) in edges.withIndex()) {
                assertEquals((i + 1) * 120f, e.x, 2f)
                assertEquals(slope, e.slope, .015f)
            }
            val corners = SpineRegion(edges[0], edges[1]).sourceCorners(height)
            assertArrayEquals(floatArrayOf(120 + offset(0), 0f, 240 + offset(0), 0f,
                240 + offset(height), height.toFloat(), 120 + offset(height), height.toFloat()), corners, 3f)
            for (y in listOf(0, height / 2, height - 1)) {
                val t = y.toFloat() / height
                val left = corners[0] + (corners[6] - corners[0]) * t
                val right = corners[2] + (corners[4] - corners[2]) * t
                assertEquals(colors[1], pixels[y * width + left.toInt() + 4])
                assertEquals(colors[1], pixels[y * width + right.toInt() - 4])
            }
        }
    }
}

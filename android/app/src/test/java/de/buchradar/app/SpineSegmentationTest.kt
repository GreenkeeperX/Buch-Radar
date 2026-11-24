package de.buchradar.app

import de.buchradar.app.scanner.OcrPiece
import de.buchradar.app.scanner.spineBand
import de.buchradar.app.scanner.spineEdges
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class SpineSegmentationTest {
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
}

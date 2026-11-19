package de.buchradar.app

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

    @Test fun flatImageDoesNotInventBooks() {
        assertTrue(spineEdges(IntArray(320 * 240) { 0xff334455.toInt() }, 320, 240).isEmpty())
        assertTrue(spineEdges(IntArray(3), 320, 240).isEmpty())
    }
}

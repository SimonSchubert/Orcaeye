package com.inspiredandroid.orcaeye.ui

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatRunInTest {
    private val from = LocalDateTime(2026, 8, 1, 18, 0)

    @Test
    fun formatsMinutes() {
        assertEquals("1 minute", formatRunIn(LocalDateTime(2026, 8, 1, 18, 1), from))
        assertEquals("45 minutes", formatRunIn(LocalDateTime(2026, 8, 1, 18, 45), from))
    }

    @Test
    fun formatsHours() {
        assertEquals("1 hour", formatRunIn(LocalDateTime(2026, 8, 1, 19, 0), from))
        assertEquals("2 hours", formatRunIn(LocalDateTime(2026, 8, 1, 20, 0), from))
        assertEquals("12 hours", formatRunIn(LocalDateTime(2026, 8, 2, 6, 0), from))
    }

    @Test
    fun formatsDays() {
        assertEquals("1 day", formatRunIn(LocalDateTime(2026, 8, 2, 18, 0), from))
        assertEquals("3 days", formatRunIn(LocalDateTime(2026, 8, 4, 18, 0), from))
    }

    @Test
    fun floorsToLargestUnit() {
        // 2h 30m → 2 hours; 1d 5h → 1 day
        assertEquals("2 hours", formatRunIn(LocalDateTime(2026, 8, 1, 20, 30), from))
        assertEquals("1 day", formatRunIn(LocalDateTime(2026, 8, 2, 23, 0), from))
    }

    @Test
    fun pastOrEqualIsLessThanAMinute() {
        assertEquals("less than a minute", formatRunIn(from, from))
        assertEquals("less than a minute", formatRunIn(LocalDateTime(2026, 8, 1, 17, 0), from))
    }

    @Test
    fun nextRunLabelPrefixesIn() {
        assertEquals(
            "Next run in 2 hours",
            nextRunLabel(LocalDateTime(2026, 8, 1, 20, 0), from),
        )
        assertEquals(null, nextRunLabel(null, from))
        assertEquals(null, nextRunLabel(from, null))
    }
}

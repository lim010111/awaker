package com.awaker.logging

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSchemaTest {

    @Test
    fun `header carries schema version and timeline anchor`() {
        val json = JSONObject(
            LogSchema.header(
                sessionId = "s0", pkg = "com.google.android.youtube",
                wallMs = 1_700_000_000_000, elapsedNs = 123_456_789,
                model = "Pixel 8", sdk = 35, app = "0.1.0-proto",
            ),
        )
        assertEquals("header", json.getString("type"))
        assertEquals(1, json.getInt("v"))
        assertEquals(1_700_000_000_000, json.getLong("wallMs"))
        assertEquals(123_456_789, json.getLong("elapsedNs"))
        assertEquals("com.google.android.youtube", json.getString("pkg"))
    }

    @Test
    fun `imu line round-trips floats`() {
        val json = JSONObject(LogSchema.imu(LogSchema.TYPE_GYRO, 42L, -0.125f, 1.5f, 3.0E-4f))
        assertEquals("gyro", json.getString("type"))
        assertEquals(42L, json.getLong("t"))
        assertEquals(-0.125, json.getDouble("x"), 1e-9)
        assertEquals(3.0E-4, json.getDouble("z"), 1e-9)
    }

    @Test
    fun `session end line carries reason and both end timestamps`() {
        val json = JSONObject(
            LogSchema.session(
                tNs = 9L, event = "end", sessionId = "s1", pkg = "com.instagram.android",
                reason = "AWAY_TIMEOUT", endedAtWallMs = 555L,
            ),
        )
        assertEquals("end", json.getString("event"))
        assertEquals("AWAY_TIMEOUT", json.getString("reason"))
        assertEquals(555L, json.getLong("endedAtWallMs"))
        assertTrue(!json.has("awayMs"))
    }

    @Test
    fun `foreground null is JSON null`() {
        val json = JSONObject(LogSchema.foreground(7L, null))
        assertTrue(json.isNull("pkg"))
    }

    @Test
    fun `strings with quotes are escaped`() {
        val json = JSONObject(
            LogSchema.header("a\"b", "p", 0, 0, model = "M\\X", sdk = 1, app = "v"),
        )
        assertEquals("a\"b", json.getString("sessionId"))
        assertEquals("M\\X", json.getString("model"))
    }

    @Test
    fun `screen light battery lines parse`() {
        assertEquals(true, JSONObject(LogSchema.screen(1L, true)).getBoolean("on"))
        assertEquals(12.5, JSONObject(LogSchema.light(1L, 12.5f)).getDouble("lux"), 1e-9)
        val battery = JSONObject(LogSchema.battery(1L, 88, charging = false))
        assertEquals(88, battery.getInt("pct"))
        assertEquals(false, battery.getBoolean("charging"))
    }
}

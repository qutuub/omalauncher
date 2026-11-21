package dev.omalauncher.personal.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class HomeViewModelTest {
    private val model = HomeViewModel(StatusLabels("wifi", "sim", "airplane", "alarm"))
    private val now = ZonedDateTime.parse("2026-11-20T17:05:09+06:00[Asia/Dhaka]")

    @Test
    fun formatsTimeAndDateAndOmitsMissingReadings() {
        val status = model.format(StatusReading(now))
        assertEquals("17:05", status.time)
        assertEquals("17:05:09", status.clockSeconds)
        assertEquals("2026-11-20 · w47", status.isoDate)
        assertEquals("fri 20 nov", status.date)
        assertEquals("", status.indicators)
        assertEquals("", status.battery)
        assertFalse(status.lowBattery)
        assertNull(status.alarm)
        assertEquals("sat 5 dec", model.format(StatusReading(now.plusDays(15))).date)
    }

    @Test
    fun formatsWifiAndCarrierAndUsesSimFallback() {
        val reading = StatusReading(now, wifi = true, hasSim = true, carrier = " Carrier ", signal = 4)
        assertEquals("wifi  Carrier 4/4", model.format(reading).indicators)
        assertEquals("wifi  sim 0/4", model.format(reading.copy(carrier = " ", signal = 0)).indicators)
        assertEquals("wifi  sim", model.format(reading.copy(carrier = null, signal = null)).indicators)
    }

    @Test
    fun omitsCellularReadingsWhenAirplaneModeIsOnOrSimIsAbsent() {
        val reading = StatusReading(now, wifi = true, hasSim = true, carrier = "Carrier", signal = 3)
        assertEquals("wifi  airplane", model.format(reading.copy(airplane = true)).indicators)
        assertEquals("wifi", model.format(reading.copy(hasSim = false)).indicators)
    }

    @Test
    fun marksLowBatteryAtTwentyAndShowsChargingIndependently() {
        val low = model.format(StatusReading(now, batteryPercent = 20, charging = true))
        assertEquals("20% +", low.battery)
        assertTrue(low.lowBattery)
        val normal = model.format(StatusReading(now, batteryPercent = 21))
        assertEquals("21%", normal.battery)
        assertFalse(normal.lowBattery)
        assertEquals("+", model.format(StatusReading(now, charging = true)).battery)
    }

    @Test
    fun omitsOutOfRangeSignalLevels() {
        for (level in listOf(-1, 5)) {
            val status = model.format(StatusReading(now, hasSim = true, carrier = "Carrier", signal = level))
            assertEquals("Carrier", status.indicators)
        }
    }

    @Test
    fun omitsOutOfRangeBatteryPercentages() {
        for (percent in listOf(-1, 101)) {
            val status = model.format(StatusReading(now, batteryPercent = percent, charging = true))
            assertEquals("+", status.battery)
            assertFalse(status.lowBattery)
        }
    }

    @Test
    fun formatsNextAlarmInCurrentTimeZone() {
        val alarm = ZonedDateTime.parse("2026-11-21T00:30:00Z")
        assertEquals("alarm 06:30", model.format(StatusReading(now, nextAlarm = alarm)).alarm)
    }

    @Test
    fun formatsHardwareTelemetryRowsAndBars() {
        val gb = 1024L * 1024L * 1024L
        val status = model.format(
            StatusReading(
                now = now,
                wifi = true,
                hasSim = true,
                carrier = "GP",
                signal = 4,
                batteryPercent = 84,
                batteryTempC = 31.4,
                batteryVoltageV = 4.18,
                batteryCurrentMa = 290,
                uptimeMs = (14 * 60 + 22) * 60_000L,
                deviceModel = "SM-A346E",
                abi = "arm64",
                cpuCores = 8,
                ramUsedBytes = 4L * gb,
                ramTotalBytes = 8L * gb,
                diskUsedBytes = 64L * gb,
                diskTotalBytes = 128L * gb,
                netInterface = "wlan0",
                localIp = "192.168.1.42",
                androidRelease = "15",
                sdkInt = 35,
                kernelVersion = "5.15",
                archState = "ready",
            ),
        )
        assertEquals("SM-A346E · arm64 · 8c", status.machine)
        assertEquals("up 14h 22m", status.uptime)
        val byTag = status.telemetry.associateBy { it.tag }
        assertEquals("84% dis · 31.4°C · 4.18V · -290mA", byTag["PWR"]?.value)
        assertFalse(byTag["PWR"]!!.warn)
        assertEquals("4.0/8.0G  [█████░░░░░]  50%", byTag["MEM"]?.value)
        assertEquals("64/128G  [█████░░░░░]  50%", byTag["DSK"]?.value)
        assertEquals("wlan0 · 192.168.1.42 · GP 4/4", byTag["NET"]?.value)
        assertEquals("arch: ready · a15 · api 35 · k 5.15", byTag["SYS"]?.value)
    }

    @Test
    fun parsesAndCompletesVaultTasks() {
        val md = """
            # todo
            - [x] done item
            - [ ] first task @due(2026-11-20)
            - [ ] second task @due(2026-11-22)
            - [ ] plain task
        """.trimIndent()
        val tasks = parseTodoMarkdown(md)
        assertEquals(3, tasks.size)
        assertEquals("first task", tasks[0].text)
        assertEquals(LocalDate.of(2026, 11, 20), tasks[0].due)
        assertEquals(DueBadge("today", urgent = true), formatDueBadge(tasks[0].due!!, LocalDate.of(2026, 11, 20)))
        assertEquals(DueBadge("sun", urgent = false), formatDueBadge(tasks[1].due!!, LocalDate.of(2026, 11, 20)))

        val updated = completeTaskLine(md, tasks[0].lineIndex, tasks[0].rawLine)
        assertEquals(2, parseTodoMarkdown(updated!!).size)
    }
}

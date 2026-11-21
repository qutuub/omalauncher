package dev.omalauncher.personal.home

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class StatusReading(
    val now: ZonedDateTime,
    val wifi: Boolean = false,
    val hasSim: Boolean = false,
    val carrier: String? = null,
    val signal: Int? = null,
    val airplane: Boolean = false,
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val nextAlarm: ZonedDateTime? = null,
    val uptimeMs: Long? = null,
    val deviceModel: String? = null,
    val abi: String? = null,
    val cpuCores: Int? = null,
    val batteryTempC: Double? = null,
    val batteryVoltageV: Double? = null,
    val batteryCurrentMa: Int? = null,
    val batteryFull: Boolean = false,
    val ramUsedBytes: Long? = null,
    val ramTotalBytes: Long? = null,
    val diskUsedBytes: Long? = null,
    val diskTotalBytes: Long? = null,
    val netInterface: String? = null,
    val localIp: String? = null,
    val androidRelease: String? = null,
    val sdkInt: Int? = null,
    val kernelVersion: String? = null,
    val archState: String? = null,
)

internal data class StatusLabels(val wifi: String, val sim: String, val airplane: String, val alarm: String)

internal data class TelemetryRow(val tag: String, val value: String, val warn: Boolean = false)

internal data class HomeStatus(
    val time: String,
    val indicators: String,
    val battery: String,
    val lowBattery: Boolean,
    val date: String,
    val alarm: String?,
    val clockSeconds: String,
    val isoDate: String,
    val machine: String,
    val uptime: String?,
    val telemetry: List<TelemetryRow>,
)

internal class HomeViewModel(private val labels: StatusLabels) {
    fun format(reading: StatusReading): HomeStatus {
        val indicators = buildList {
            if (reading.wifi) add(labels.wifi)
            if (reading.hasSim && !reading.airplane) {
                val carrier = reading.carrier?.trim()?.takeIf { it.isNotEmpty() } ?: labels.sim
                val signal = reading.signal?.takeIf { it in 0..4 }
                add(if (signal == null) carrier else "$carrier $signal/4")
            }
            if (reading.airplane) add(labels.airplane)
        }
        val percent = reading.batteryPercent?.takeIf { it in 0..100 }
        val lowBattery = percent != null && percent <= 20
        val battery = listOfNotNull(percent?.let { "$it%" }, "+".takeIf { reading.charging }).joinToString(" ")
        val alarm = reading.nextAlarm?.let { "${labels.alarm} ${timeFormat.format(it.withZoneSameInstant(reading.now.zone))}" }

        val week = reading.now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val isoDate = "${isoDateFormat.format(reading.now)} · w%02d".format(Locale.ROOT, week)

        val machine = buildList {
            reading.deviceModel?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            reading.abi?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            reading.cpuCores?.takeIf { it > 0 }?.let { add("${it}c") }
        }.joinToString(" · ")

        val uptime = reading.uptimeMs?.takeIf { it >= 0 }?.let { "up ${formatUptime(it)}" }

        val telemetry = buildList {
            formatPower(reading, percent)?.let { add(TelemetryRow("PWR", it, warn = lowBattery || (reading.batteryTempC ?: 0.0) >= 42.0)) }
            formatUsage(reading.ramUsedBytes, reading.ramTotalBytes)?.let { add(TelemetryRow("MEM", it)) }
            formatUsage(reading.diskUsedBytes, reading.diskTotalBytes)?.let { add(TelemetryRow("DSK", it)) }
            formatNet(reading, indicators)?.let { add(TelemetryRow("NET", it)) }
            formatSys(reading)?.let { add(TelemetryRow("SYS", it)) }
        }

        return HomeStatus(
            time = timeFormat.format(reading.now),
            indicators = indicators.joinToString("  "),
            battery = battery,
            lowBattery = lowBattery,
            date = dateFormat.format(reading.now).lowercase(Locale.ROOT),
            alarm = alarm,
            clockSeconds = clockSecondsFormat.format(reading.now),
            isoDate = isoDate,
            machine = machine,
            uptime = uptime,
            telemetry = telemetry,
        )
    }

    private fun formatPower(reading: StatusReading, percent: Int?): String? {
        val parts = buildList {
            if (percent != null) {
                val mode = when {
                    reading.batteryFull -> "full"
                    reading.charging -> "chg"
                    else -> "dis"
                }
                add("$percent% $mode")
            }
            reading.batteryTempC?.takeIf { it in -20.0..100.0 }?.let {
                add(String.format(Locale.ROOT, "%.1f°C", it))
            }
            reading.batteryVoltageV?.takeIf { it in 1.0..15.0 }?.let {
                add(String.format(Locale.ROOT, "%.2fV", it))
            }
            reading.batteryCurrentMa?.takeIf { it != 0 && abs(it) < 20_000 }?.let { ma ->
                val signed = when {
                    reading.charging -> abs(ma)
                    reading.batteryFull -> 0
                    else -> -abs(ma)
                }
                if (signed != 0) add(if (signed > 0) "+${signed}mA" else "${signed}mA")
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun formatUsage(usedBytes: Long?, totalBytes: Long?): String? {
        if (usedBytes == null || totalBytes == null || totalBytes <= 0L || usedBytes < 0L) return null
        val clampedUsed = usedBytes.coerceAtMost(totalBytes)
        val fraction = clampedUsed.toDouble() / totalBytes.toDouble()
        val pct = (fraction * 100.0).roundToInt().coerceIn(0, 100)
        val usedGb = clampedUsed.toDouble() / GB
        val totalGb = totalBytes.toDouble() / GB
        val numbers = if (totalGb >= 20.0) {
            String.format(Locale.ROOT, "%.0f/%.0fG", usedGb, totalGb)
        } else {
            String.format(Locale.ROOT, "%.1f/%.1fG", usedGb, totalGb)
        }
        return "$numbers  ${asciiBar(fraction)}  $pct%"
    }

    private fun formatNet(reading: StatusReading, indicators: List<String>): String? {
        val parts = buildList {
            reading.netInterface?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            reading.localIp?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            if (isEmpty() && reading.wifi) add(labels.wifi)
            if (reading.airplane) {
                add(labels.airplane)
            } else if (reading.hasSim) {
                val carrier = reading.carrier?.trim()?.takeIf { it.isNotEmpty() } ?: labels.sim
                val signal = reading.signal?.takeIf { it in 0..4 }
                add(if (signal == null) carrier else "$carrier $signal/4")
            }
        }
        val effective = parts.ifEmpty { indicators }
        return if (effective.isEmpty()) "offline" else effective.joinToString(" · ")
    }

    private fun formatSys(reading: StatusReading): String? {
        val parts = buildList {
            reading.archState?.trim()?.takeIf { it.isNotEmpty() }?.let { add("arch: $it") }
            reading.androidRelease?.trim()?.takeIf { it.isNotEmpty() }?.let { add("a$it") }
            reading.sdkInt?.takeIf { it > 0 }?.let { add("api $it") }
            reading.kernelVersion?.trim()?.takeIf { it.isNotEmpty() }?.let { add("k $it") }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    internal companion object {
        private const val GB = 1024.0 * 1024.0 * 1024.0
        private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
        private val clockSecondsFormat = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
        private val isoDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
        // Keep the date in English to match the lowercase English home design.
        private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

        internal fun asciiBar(fraction: Double, width: Int = 10): String {
            val filled = (fraction.coerceIn(0.0, 1.0) * width).roundToInt().coerceIn(0, width)
            return "[" + "█".repeat(filled) + "░".repeat(width - filled) + "]"
        }

        internal fun formatUptime(ms: Long): String {
            val totalMinutes = ms / 60_000L
            val days = totalMinutes / (24L * 60L)
            val hours = (totalMinutes / 60L) % 24L
            val minutes = totalMinutes % 60L
            return when {
                days > 0L -> "${days}d %02dh".format(Locale.ROOT, hours)
                hours > 0L -> "${hours}h %02dm".format(Locale.ROOT, minutes)
                else -> "${minutes}m"
            }
        }
    }
}

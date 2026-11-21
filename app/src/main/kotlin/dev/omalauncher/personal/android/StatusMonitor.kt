package dev.omalauncher.personal.android

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import dev.omalauncher.personal.home.StatusReading
import java.net.Inet4Address
import java.time.Instant
import java.time.ZonedDateTime

/** One subscription per visible period. All updates are delivered on the main thread. */
internal class StatusMonitor(
    private val context: Context,
    private val archState: () -> String? = { null },
    private val onReading: (StatusReading) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val features = context.packageManager
    private val hasRadio = features.hasSystemFeature(
        if (Build.VERSION.SDK_INT >= 33) PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS else PackageManager.FEATURE_TELEPHONY,
    )
    private val hasSubscriptions = features.hasSystemFeature(
        if (Build.VERSION.SDK_INT >= 33) PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION else PackageManager.FEATURE_TELEPHONY,
    )
    private val wifiNetworks = mutableSetOf<Network>()
    private var battery: Intent? = null
    private var active = false
    private var receiverRegistrationAttempted = false
    private var networkRegistrationAttempted = false
    private var signalAccessDenied = false

    private val shortAbi = Build.SUPPORTED_ABIS.firstOrNull()?.let { abi ->
        when {
            abi.startsWith("arm64") -> "arm64"
            abi.startsWith("armeabi") -> "arm32"
            else -> abi
        }
    }
    private val cpuCores = Runtime.getRuntime().availableProcessors()
    private val shortKernel = System.getProperty("os.version")?.substringBefore('-')?.takeIf { it.isNotBlank() }

    private var cachedRamUsed: Long? = null
    private var cachedRamTotal: Long? = null
    private var cachedDiskUsed: Long? = null
    private var cachedDiskTotal: Long? = null
    private var cachedNetInterface: String? = null
    private var cachedLocalIp: String? = null
    private var tickCount = 0

    private val ticker = object : Runnable {
        override fun run() {
            if (!active) return
            if (tickCount % 5 == 0) refreshSystemMetrics()
            tickCount++
            publish()
            mainHandler.postDelayed(this, 1000L)
        }
    }

    private val broadcasts = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) battery = intent
            publish()
        }
    }

    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            wifiNetworks.add(network)
            refreshNetworkDetails()
            publish()
        }

        override fun onLost(network: Network) {
            wifiNetworks.remove(network)
            refreshNetworkDetails()
            publish()
        }
    }

    private var stopSignalListener: (() -> Unit)? = null

    fun start() {
        if (active) return
        active = true
        tickCount = 0
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            }
            receiverRegistrationAttempted = true
            battery = ContextCompat.registerReceiver(context, broadcasts, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

            val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
            networkRegistrationAttempted = true
            connectivity.registerNetworkCallback(request, network, mainHandler)
            startSignalListener()
            refreshSystemMetrics()
            publish()
            mainHandler.postDelayed(ticker, 1000L)
        } catch (failure: RuntimeException) {
            Log.w(TAG, "Could not register status updates", failure)
            publish()
            stop()
        }
    }

    private fun startSignalListener() {
        // Phone-state access can be enforced at registration. Without it, refresh snapshots on ticks.
        if (hasRadio && telephony != null && ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_STATE,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                if (Build.VERSION.SDK_INT >= 31) listenForSignal(telephony) else listenForLegacySignal(telephony)
            } catch (failure: RuntimeException) {
                signalAccessDenied = true
                Log.w(TAG, "Could not register signal updates", failure)
                unregisterSignalListener()
            }
        }
    }

    fun stop() {
        active = false
        mainHandler.removeCallbacks(ticker)
        unregisterSignalListener()
        if (networkRegistrationAttempted) {
            networkRegistrationAttempted = false
            unregister { connectivity.unregisterNetworkCallback(network) }
        }
        if (receiverRegistrationAttempted) {
            receiverRegistrationAttempted = false
            unregister { context.unregisterReceiver(broadcasts) }
        }
        wifiNetworks.clear()
        battery = null
        signalAccessDenied = false
    }

    private fun refreshSystemMetrics() {
        try {
            val info = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(info)
            cachedRamTotal = info.totalMem
            cachedRamUsed = (info.totalMem - info.availMem).coerceAtLeast(0L)
        } catch (failure: RuntimeException) {
            Log.w(TAG, "Could not read memory info", failure)
        }
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            cachedDiskTotal = stat.totalBytes
            cachedDiskUsed = (stat.totalBytes - stat.availableBytes).coerceAtLeast(0L)
        } catch (failure: RuntimeException) {
            Log.w(TAG, "Could not read storage info", failure)
        }
        refreshNetworkDetails()
    }

    private fun refreshNetworkDetails() {
        try {
            val activeNet = connectivity.activeNetwork
            val props = activeNet?.let { connectivity.getLinkProperties(it) }
            cachedNetInterface = props?.interfaceName
            cachedLocalIp = props?.linkAddresses
                ?.mapNotNull { it.address }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        } catch (failure: RuntimeException) {
            cachedNetInterface = null
            cachedLocalIp = null
            Log.w(TAG, "Could not read network link properties", failure)
        }
    }

    private fun unregisterSignalListener() {
        val unregisterSignal = stopSignalListener
        stopSignalListener = null
        if (unregisterSignal != null) unregister(unregisterSignal)
    }

    private fun unregister(action: () -> Unit) {
        try {
            action()
        } catch (failure: RuntimeException) {
            Log.w(TAG, "Could not unregister status updates", failure)
        }
    }

    @RequiresApi(31)
    private fun listenForSignal(manager: TelephonyManager) {
        val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = publish()
        }
        stopSignalListener = { manager.unregisterTelephonyCallback(callback) }
        manager.registerTelephonyCallback(context.mainExecutor, callback)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private fun listenForLegacySignal(manager: TelephonyManager) {
        val listener = object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = publish()
        }
        stopSignalListener = { manager.listen(listener, PhoneStateListener.LISTEN_NONE) }
        manager.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
    }

    private fun publish() {
        if (!active) return
        try {
            onReading(readStatus())
        } catch (failure: RuntimeException) {
            Log.w(TAG, "Could not read Home status", failure)
        }
    }

    private fun readStatus(): StatusReading {
        val now = ZonedDateTime.now()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val tempTenths = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val voltageMv = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val currentUa = runCatching {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrDefault(Int.MIN_VALUE)
        val currentMa = if (currentUa != Int.MIN_VALUE && currentUa != 0) currentUa / 1000 else null

        val airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        val reading = StatusReading(
            now = now,
            wifi = wifiNetworks.isNotEmpty(),
            airplane = airplane,
            batteryPercent = if (scale > 0 && level in 0..scale) (100L * level / scale).toInt() else null,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING,
            batteryFull = status == BatteryManager.BATTERY_STATUS_FULL,
            batteryTempC = if (tempTenths != Int.MIN_VALUE) tempTenths / 10.0 else null,
            batteryVoltageV = if (voltageMv > 0) voltageMv / 1000.0 else null,
            batteryCurrentMa = currentMa,
            nextAlarm = alarms.nextAlarmClock?.let { Instant.ofEpochMilli(it.triggerTime).atZone(now.zone) },
            uptimeMs = SystemClock.elapsedRealtime(),
            deviceModel = Build.MODEL,
            abi = shortAbi,
            cpuCores = cpuCores,
            ramUsedBytes = cachedRamUsed,
            ramTotalBytes = cachedRamTotal,
            diskUsedBytes = cachedDiskUsed,
            diskTotalBytes = cachedDiskTotal,
            netInterface = cachedNetInterface,
            localIp = cachedLocalIp,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            kernelVersion = shortKernel,
            archState = archState(),
        )
        val manager = telephony ?: return reading
        if (!hasSubscriptions || airplane) return reading
        // SIM state, operator names, and the cached signal snapshot have no phone-state permission requirement.
        return try {
            if (manager.simState != TelephonyManager.SIM_STATE_READY) return reading
            val networkName = if (hasRadio) manager.networkOperatorName else null
            val carrier = networkName?.takeIf(String::isNotBlank) ?: manager.simOperatorName
            // Once the signal read is refused, stop asking: one warning, not one per second.
            val signal = if (hasRadio && !signalAccessDenied) {
                try {
                    manager.signalStrength?.level
                } catch (denied: SecurityException) {
                    signalAccessDenied = true
                    Log.w(TAG, "Signal strength access denied", denied)
                    null
                }
            } else {
                null
            }
            reading.copy(hasSim = true, carrier = carrier, signal = signal)
        } catch (denied: SecurityException) {
            Log.w(TAG, "Cellular status access denied", denied)
            reading.copy(hasSim = true)
        } catch (failure: RuntimeException) {
            Log.w(TAG, "Could not read cellular status", failure)
            reading.copy(hasSim = true)
        }
    }

    private companion object {
        const val TAG = "StatusMonitor"
    }
}

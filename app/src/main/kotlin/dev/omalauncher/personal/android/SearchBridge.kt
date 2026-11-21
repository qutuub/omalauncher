package dev.omalauncher.personal.android

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.omalauncher.R
import dev.omalauncher.apps.AndroidAppCatalog
import dev.omalauncher.personal.home.RECENT_APP_LIMIT
import dev.omalauncher.personal.home.SearchResult
import dev.omalauncher.personal.home.readRecentAppIds
import dev.omalauncher.personal.home.searchResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class SettingPage(val name: Int, val section: Int, val action: String)

// The public Settings API has no direct hotspot or wireless debugging action.
private val pages = listOfNotNull(
    SettingPage(R.string.personal_page_wifi, R.string.personal_section_connections, Settings.ACTION_WIFI_SETTINGS),
    SettingPage(R.string.personal_page_bluetooth, R.string.personal_section_connections, Settings.ACTION_BLUETOOTH_SETTINGS),
    SettingPage(R.string.personal_page_mobile, R.string.personal_section_connections, Settings.ACTION_DATA_ROAMING_SETTINGS),
    SettingPage(R.string.personal_page_airplane, R.string.personal_section_connections, Settings.ACTION_AIRPLANE_MODE_SETTINGS),
    SettingPage(R.string.personal_page_display, R.string.personal_section_display, Settings.ACTION_DISPLAY_SETTINGS),
    SettingPage(R.string.personal_page_battery, R.string.personal_section_battery, Settings.ACTION_BATTERY_SAVER_SETTINGS),
    SettingPage(R.string.personal_page_sound, R.string.personal_section_sounds, Settings.ACTION_SOUND_SETTINGS),
    if (Build.VERSION.SDK_INT >= 33) {
        SettingPage(R.string.personal_page_notifications, R.string.personal_section_notifications, Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS)
    } else {
        null
    },
    SettingPage(R.string.personal_page_app_notifications, R.string.personal_section_notifications, Settings.ACTION_APP_NOTIFICATION_SETTINGS),
    SettingPage(R.string.personal_page_dnd, R.string.personal_section_notifications, Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS),
    SettingPage(R.string.personal_page_apps, R.string.personal_section_apps, Settings.ACTION_APPLICATION_SETTINGS),
    SettingPage(R.string.personal_page_defaults, R.string.personal_section_apps, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
    SettingPage(R.string.personal_page_location, R.string.personal_section_location, Settings.ACTION_LOCATION_SOURCE_SETTINGS),
    SettingPage(R.string.personal_page_storage, R.string.personal_section_device, Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    SettingPage(R.string.personal_page_date, R.string.personal_section_general, Settings.ACTION_DATE_SETTINGS),
    SettingPage(R.string.personal_page_keyboard, R.string.personal_section_general, Settings.ACTION_INPUT_METHOD_SETTINGS),
    SettingPage(R.string.personal_page_developer, R.string.personal_section_developer, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    SettingPage(R.string.personal_page_debugging, R.string.personal_section_developer, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    SettingPage(R.string.personal_page_about, R.string.personal_section_about, Settings.ACTION_DEVICE_INFO_SETTINGS),
    SettingPage(R.string.personal_page_accessibility, R.string.personal_section_accessibility, Settings.ACTION_ACCESSIBILITY_SETTINGS),
    SettingPage(R.string.personal_page_security, R.string.personal_section_security, Settings.ACTION_SECURITY_SETTINGS),
    SettingPage(R.string.personal_page_accounts, R.string.personal_section_accounts, Settings.ACTION_SYNC_SETTINGS),
    SettingPage(R.string.personal_page_info, R.string.personal_section_apps, Settings.ACTION_APPLICATION_DETAILS_SETTINGS),
    SettingPage(R.string.personal_page_settings, R.string.personal_section_general, Settings.ACTION_SETTINGS),
)

internal class SearchBridge(private val context: Context, private val catalog: AndroidAppCatalog?) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val recentsStore = context.getSharedPreferences("personal_search_v1", Context.MODE_PRIVATE)
    private var recents by mutableStateOf(readRecentAppIds(recentsStore.all["recent_apps"] as? String))
    private var settings by mutableStateOf(emptyMap<SearchResult, Intent?>())

    suspend fun refreshSettings() {
        settings = withContext(Dispatchers.IO) {
            pages.associate { page ->
                val result = SearchResult("setting:${page.name}", context.getString(page.name), context.getString(page.section))
                val intent = Intent(page.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply {
                    when (page.action) {
                        Settings.ACTION_APP_NOTIFICATION_SETTINGS -> putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS -> data = Uri.fromParts("package", context.packageName, null)
                    }
                }
                result to intent.takeIf { it.resolveActivity(context.packageManager) != null }
            }
        }
    }

    fun results(query: String): List<SearchResult> = searchResults(
        query, catalog?.apps.orEmpty().map { SearchResult(it.key, it.label) },
        settings.keys.toList(), recents, { settings[it] != null },
    )

    fun profileLabel(result: SearchResult): Int? {
        val app = catalog?.byKey?.get(result.id) ?: return null
        return when {
            app.work -> R.string.personal_search_work_app
            app.clone -> R.string.personal_search_clone_app
            else -> null
        }
    }

    fun open(result: SearchResult): Boolean {
        try {
            if (result.section != null) {
                val intent = settings[result] ?: return false
                context.startActivity(intent)
            } else {
                val app = catalog?.byKey?.get(result.id) ?: return false
                if (userManager.isQuietModeEnabled(app.user)) {
                    userManager.requestQuietModeEnabled(false, app.user)
                    return false
                }
                launcherApps.startMainActivity(app.component, app.user, null, null)
                recents = (listOf(app.key) + recents).distinct().take(RECENT_APP_LIMIT)
                recentsStore.edit().putString("recent_apps", recents.joinToString("\n")).apply()
            }
            return true
        } catch (failure: RuntimeException) {
            Log.w("PersonalSearch", "Could not open ${result.id}", failure)
            return false
        }
    }
}

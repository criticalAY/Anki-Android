/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.analytics

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.criticalay.GoogleAnalytics
import com.criticalay.GoogleAnalyticsConfig
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.R
import com.ichi2.anki.preferences.sharedPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

// TODO: write test for this class
object AnkiDroidUsageAnalytics {
    const val ANALYTICS_OPTIN_KEY = "analytics_opt_in"
    private const val ANALYTICS_CLIENT_ID = "googleAnalyticsClientId"
    private const val ANALYTICS_PREFS = "analyticsPrefs"

    @Volatile private var analytics: GoogleAnalytics? = null

    @Volatile private var optIn = false

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val clientId: String by lazy { getOrCreateClientId(AnkiDroidApp.instance) }

    private val sharedPrefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == ANALYTICS_OPTIN_KEY) {
                optIn = prefs.getBoolean(key, false)
                Timber.i("Setting analytics opt-in to: %b", optIn)
            }
        }

    private var samplePercentage: Int = -1

    lateinit var preferencesWhoseChangesShouldBeReported: Set<String>
        private set

    fun initialize(context: Context) {
        val appContext = context.applicationContext

        Timber.i("AnkiDroidUsageAnalytics:: initialize()")

        if (analytics == null) {
            analytics =
                GoogleAnalytics.builder {
                    measurementId = appContext.getString(R.string.ga_trackingId)
                    apiSecret = BuildConfig.ANALYTICS_API_KEY
                    appName = appContext.getString(R.string.app_name)
                    appVersion = BuildConfig.VERSION_NAME
                    enabled = true
                    samplePercentage = getAnalyticsSamplePercentage(appContext)
                    debug = false
                }
        }

        handlePreferences(appContext)
        initializePrefKeys(appContext)

        AnalyticsExceptionHandler.install(this::sendAnalyticsException)
    }

    private fun handlePreferences(context: Context) {
        val userPrefs = context.sharedPrefs()
        optIn = userPrefs.getBoolean(ANALYTICS_OPTIN_KEY, false)
        userPrefs.registerOnSharedPreferenceChangeListener(sharedPrefsListener)
    }

    fun reinitialize(context: Context) {
        Timber.i("reInitialize()")
        AnalyticsExceptionHandler.uninstall()

        serviceScope.launch {
            runCatching { analytics?.flush() }.onFailure { e ->
                Timber.e(e, "Failed to flush analytics")
            }
            analytics = null
            initialize(context)
        }
    }

    fun sendAnalyticsScreenView(screen: Any) = sendAnalyticsScreenView(screen.javaClass.simpleName)

    fun sendAnalyticsScreenView(screenName: String) {
        Timber.d("AnkiDroidUsageAnalytics: screenView($screenName)")
        if (!optIn) return
        analytics?.screenView(clientId)?.screenName(screenName)?.sendAsync()
    }

    fun sendAnalyticsEvent(
        category: String,
        action: String,
        value: Int? = null,
        label: String? = null,
    ) {
        Timber.d("AnkiDroidUsageAnalytics: event(category=$category action=$action)")
        if (!optIn) return
        analytics
            ?.event(clientId)
            ?.category(category)
            ?.action(action)
            ?.apply { label?.let { label(it) } }
            ?.apply { value?.let { value(it) } }
            ?.sendAsync()
    }

    fun sendAnalyticsException(
        t: Throwable,
        fatal: Boolean,
    ) {
        val cause = getRootCause(t)
        sendAnalyticsException("${cause::class.simpleName}: ${cause.message}", fatal)
    }

    fun sendAnalyticsException(
        description: String,
        fatal: Boolean,
    ) {
        if (!optIn) return

        Timber.d("AnkiDroidUsageAnalytics: exception(fatal=$fatal)")
        analytics
            ?.exception(clientId)
            ?.description(description.take(150))
            ?.fatal(fatal)
            ?.sendAsync()
    }

    private fun getRootCause(t: Throwable): Throwable {
        var result = t
        while (result.cause != null && result.cause !== result) {
            result = result.cause!!
        }
        return result
    }

    private fun getOrCreateClientId(context: Context): String {
        Timber.d("AnkiDroidUsageAnalytics:: getting client Id")
        val prefs = context.getSharedPreferences(ANALYTICS_PREFS, Context.MODE_PRIVATE)
        return prefs.getString(ANALYTICS_CLIENT_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString(ANALYTICS_CLIENT_ID, it) }
        }
    }

    private fun getAnalyticsSamplePercentage(context: Context): Int {
        Timber.d("AnkiDroidUsageAnalytics:: getting sample percentage")
        if (samplePercentage == -1) {
            samplePercentage = context.resources.getInteger(R.integer.ga_sampleFrequency)
        }
        return samplePercentage
    }

    private fun initializePrefKeys(context: Context) {
        Timber.d("AnkiDroidUsageAnalytics:: initializing pref keys")
        preferencesWhoseChangesShouldBeReported =
            AnalyticsConstants.reportablePrefKeys.mapTo(
                HashSet(AnalyticsConstants.reportablePrefKeys.size),
            ) { context.getString(it) }
    }

    var isEnabled: Boolean
        get() = optIn
        set(value) {

            AnkiDroidApp.instance.sharedPrefs().edit {
                putBoolean(ANALYTICS_OPTIN_KEY, value)
            }
        }

    fun setDevMode(context: Context) {
        Timber.d("setDevMode() re-configuring for development analytics tagging")
        samplePercentage = 100
        reinitialize(context)
    }
}

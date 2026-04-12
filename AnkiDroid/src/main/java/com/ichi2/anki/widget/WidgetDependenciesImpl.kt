/*
 *  Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.widget

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.IntentHandler.Companion.intentToReviewDeckFromShortcuts
import com.ichi2.anki.MetaDB
import com.ichi2.anki.R
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.pages.DeckOptionsDestination
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.settings.Prefs
import com.ichi2.widget.AddNoteWidget
import com.ichi2.widget.AnkiDroidWidgetSmall
import com.ichi2.widget.SmallWidgetStatus
import com.ichi2.widget.bridge.WidgetAnalytics
import com.ichi2.widget.bridge.WidgetAppState
import com.ichi2.widget.bridge.WidgetCollectionAccess
import com.ichi2.widget.bridge.WidgetCrashReporter
import com.ichi2.widget.bridge.WidgetDependencies
import com.ichi2.widget.bridge.WidgetIntentFactory
import com.ichi2.widget.bridge.WidgetMetaStorage
import com.ichi2.widget.bridge.WidgetPreferences
import com.ichi2.widget.getAppWidgetIdsEx
import com.ichi2.widget.getAppWidgetManager
import kotlinx.coroutines.CoroutineScope

class WidgetAnalyticsImpl : WidgetAnalytics {
    override fun sendAnalyticsEvent(
        category: String,
        action: String,
        value: Int?,
        label: String?,
    ) {
        com.ichi2.anki.analytics.UsageAnalytics
            .sendAnalyticsEvent(category, action, value, label)
    }
}

class WidgetIntentFactoryImpl : WidgetIntentFactory {
    override fun grantedStoragePermissions(
        context: Context,
        showToast: Boolean,
    ): Boolean = IntentHandler.grantedStoragePermissions(context, showToast)

    override fun intentToReviewDeck(
        context: Context,
        deckId: DeckId,
    ): Intent = intentToReviewDeckFromShortcuts(context, deckId)

    override fun intentToOpenNoteEditor(context: Context): Intent = NoteEditorLauncher.AddNote().toIntent(context)

    override fun intentToMainActivity(context: Context): Intent =
        Intent(context, IntentHandler::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

    override suspend fun intentToDeckOptions(
        context: Context,
        deckId: DeckId,
    ): Intent = DeckOptionsDestination.fromDeckId(deckId).toIntent(context)
}

class WidgetCollectionAccessImpl : WidgetCollectionAccess {
    override suspend fun <T> withCol(block: Collection.() -> T): T = CollectionManager.withCol(block)

    override suspend fun isCollectionEmpty(): Boolean = com.ichi2.anki.isCollectionEmpty()
}

class WidgetAppStateImpl : WidgetAppState {
    override val isSdCardMounted: Boolean
        get() = AnkiDroidApp.isSdCardMounted

    override val applicationScope: CoroutineScope
        get() = AnkiDroidApp.applicationScope

    override val applicationInstance: Application
        get() = AnkiDroidApp.instance

    override fun scheduleNotification(context: Context) {
        (context.applicationContext as AnkiDroidApp).scheduleNotification()
    }

    override fun updateSmallWidgetUi(context: Context) {
        AnkiDroidWidgetSmall
            .UpdateService()
            .doUpdate(context)
    }

    override fun updateAddNoteWidgets(context: Context) {
        val appWidgetManager = getAppWidgetManager(context) ?: return
        val widgetIds =
            appWidgetManager.getAppWidgetIdsEx(
                android.content.ComponentName(context, AddNoteWidget::class.java),
            )
        AddNoteWidget
            .updateWidgets(context, appWidgetManager, widgetIds)
    }
}

class WidgetCrashReporterImpl : WidgetCrashReporter {
    override fun sendExceptionReport(
        throwable: Throwable,
        origin: String,
        onlyIfSilent: Boolean,
    ) {
        CrashReportService.sendExceptionReport(throwable, origin, onlyIfSilent = onlyIfSilent)
    }
}

class WidgetMetaStorageImpl : WidgetMetaStorage {
    override fun storeSmallWidgetStatus(
        context: Context,
        status: SmallWidgetStatus,
    ) {
        MetaDB.storeSmallWidgetStatus(context, status)
    }

    override fun getWidgetSmallStatus(context: Context): SmallWidgetStatus = MetaDB.getWidgetSmallStatus(context)

    override fun getNotificationStatus(context: Context): Int = MetaDB.getNotificationStatus(context)
}

class WidgetPreferencesImpl : WidgetPreferences {
    override fun sharedPrefs(context: Context): SharedPreferences = context.sharedPrefs()

    override val newReviewRemindersEnabled: Boolean
        get() = Prefs.newReviewRemindersEnabled

    override fun isLegacyNotificationEnabled(context: Context): Boolean {
        val preferences = context.sharedPrefs()
        return preferences
            .getString(context.getString(R.string.pref_notifications_minimum_cards_due_key), "1000001")!!
            .toInt() < 1000000
    }
}

/**
 * Initializes [WidgetDependencies] with real app-module implementations.
 * Should be called from [AnkiDroidApp.onCreate].
 */
fun initWidgetDependencies() {
    WidgetDependencies.init(
        analytics = WidgetAnalyticsImpl(),
        intentFactory = WidgetIntentFactoryImpl(),
        collectionAccess = WidgetCollectionAccessImpl(),
        appState = WidgetAppStateImpl(),
        crashReporter = WidgetCrashReporterImpl(),
        metaStorage = WidgetMetaStorageImpl(),
        preferences = WidgetPreferencesImpl(),
    )
}

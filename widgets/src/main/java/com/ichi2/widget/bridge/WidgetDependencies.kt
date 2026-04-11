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

package com.ichi2.widget.bridge

/**
 * Central registry for widget dependencies provided by the app module.
 *
 * Must be initialized in `AnkiDroidApp.onCreate()` before any widget code runs.
 */
object WidgetDependencies {
    lateinit var analytics: WidgetAnalytics
    lateinit var intentFactory: WidgetIntentFactory
    lateinit var collectionAccess: WidgetCollectionAccess
    lateinit var appState: WidgetAppState
    lateinit var crashReporter: WidgetCrashReporter
    lateinit var metaStorage: WidgetMetaStorage
    lateinit var preferences: WidgetPreferences

    fun init(
        analytics: WidgetAnalytics,
        intentFactory: WidgetIntentFactory,
        collectionAccess: WidgetCollectionAccess,
        appState: WidgetAppState,
        crashReporter: WidgetCrashReporter,
        metaStorage: WidgetMetaStorage,
        preferences: WidgetPreferences,
    ) {
        this.analytics = analytics
        this.intentFactory = intentFactory
        this.collectionAccess = collectionAccess
        this.appState = appState
        this.crashReporter = crashReporter
        this.metaStorage = metaStorage
        this.preferences = preferences
    }
}

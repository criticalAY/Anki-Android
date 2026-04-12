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

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope

/**
 * Abstraction for application-level state used by widgets.
 * Implemented in the app module using [AnkiDroidApp].
 */
interface WidgetAppState {
    /** Whether the SD card is currently mounted */
    val isSdCardMounted: Boolean

    /** Application-scoped coroutine scope for background work */
    val applicationScope: CoroutineScope

    /** The application instance, for registering receivers */
    val applicationInstance: Application

    /** Schedules a notification for review reminders */
    fun scheduleNotification(context: Context)

    /** Triggers the small widget UI update */
    fun updateSmallWidgetUi(context: Context)

    /** Triggers AddNoteWidget update after permission grant */
    fun updateAddNoteWidgets(context: Context)
}

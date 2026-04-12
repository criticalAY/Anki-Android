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

import android.content.Context
import android.content.SharedPreferences

/**
 * Abstraction for app preferences used by widgets.
 * Implemented in the app module using [sharedPrefs] and [Prefs].
 */
interface WidgetPreferences {
    /** Gets the default SharedPreferences for the given context */
    fun sharedPrefs(context: Context): SharedPreferences

    /** Whether the new review reminders feature is enabled */
    val newReviewRemindersEnabled: Boolean

    /** Whether legacy notification is enabled (minimumCardsDue < 1000000) */
    fun isLegacyNotificationEnabled(context: Context): Boolean
}

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
import android.content.Intent
import com.ichi2.anki.libanki.DeckId

/**
 * Abstraction for creating intents used by widgets.
 * Implemented in the app module using [IntentHandler], [NoteEditorLauncher], [DeckOptionsDestination].
 */
interface WidgetIntentFactory {
    /**
     * Checks if storage permissions have been granted.
     * Maps to `IntentHandler.grantedStoragePermissions`.
     */
    fun grantedStoragePermissions(
        context: Context,
        showToast: Boolean,
    ): Boolean

    /**
     * Creates an intent to review a specific deck.
     * Maps to `IntentHandler.intentToReviewDeckFromShortcuts`.
     */
    fun intentToReviewDeck(
        context: Context,
        deckId: DeckId,
    ): Intent

    /**
     * Creates an intent to open the note editor.
     * Maps to `NoteEditorLauncher.AddNote().toIntent`.
     */
    fun intentToOpenNoteEditor(context: Context): Intent

    /**
     * Creates an intent to open the main activity.
     * Maps to `Intent(context, IntentHandler::class.java)`.
     */
    fun intentToMainActivity(context: Context): Intent

    /**
     * Creates an intent to open deck options for a specific deck.
     * Maps to `DeckOptionsDestination.fromDeckId(deckId).toIntent`.
     */
    suspend fun intentToDeckOptions(
        context: Context,
        deckId: DeckId,
    ): Intent

    /** Returns the Class of the CardAnalysisWidgetConfig activity */
    fun cardAnalysisWidgetConfigClass(): Class<*>

    /** Returns the Class of the DeckPickerWidgetConfig activity */
    fun deckPickerWidgetConfigClass(): Class<*>
}

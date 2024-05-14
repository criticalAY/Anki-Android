/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
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

package com.ichi2.anki.instanteditor

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.instantnoteeditor.InstantEditorViewModel
import com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity
import com.ichi2.anki.instantnoteeditor.SaveNoteResult
import com.ichi2.libanki.removeNotetype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstantEditorViewModelTest : RobolectricTest() {

    @Test
    fun testSetUpNoteType_with_Cloze_NoteType() = runViewModelTest {
        assertEquals(InstantNoteEditorActivity.DialogType.SHOW_EDITOR_DIALOG, dialogType.value)
    }

    @Test
    fun testSetUpNoteType_with_NoCloze_NoteType() = runViewModelTest {
        val noteTypes = col.notetypes.all().filter { it.isCloze }

        for (note in noteTypes) {
            col.backend.removeNotetype(note.id)
        }

        waitForAsyncTasksToComplete()

        // Reinitialize the viewModel
        runViewModelTest({ InstantEditorViewModel() }) {
            assertEquals(
                InstantNoteEditorActivity.DialogType.NO_CLOZE_NOTE_TYPES_DIALOG,
                dialogType.value
            )
        }
    }

    @Test
    fun testSavingNoteWithNoCloze() = runViewModelTest {
        editorNote.setField(0, "Hello")
        val result = checkAndSaveNote(targetContext)

        assertEquals(CollectionManager.TR.addingYouHaveAClozeDeletionNote(), saveNoteResult(result))
    }

    @Test
    fun testSavingNoteWithEmptyFields() = runViewModelTest {
        editorNote.setField(0, "{{c1::Hello}}")

        val result = checkAndSaveNote(targetContext)

        assertEquals("Success", saveNoteResult(result))
    }

    @Test
    fun testSavingNoteWithClozeFields() = runViewModelTest {
        val result = checkAndSaveNote(targetContext)

        assertEquals(CollectionManager.TR.addingTheFirstFieldIsEmpty(), saveNoteResult(result))
    }

    @Test
    fun testCheckAndSaveNote_NullEditorNote_ReturnsFailure() = runViewModelTest {
        val result = checkAndSaveNote(targetContext)

        assertTrue(result is SaveNoteResult.Warning)
    }

    private fun runViewModelTest(
        initViewModel: () -> InstantEditorViewModel = { InstantEditorViewModel() },
        testBody: suspend InstantEditorViewModel.() -> Unit
    ) = runTest {
        val viewModel = initViewModel()
        testBody(viewModel)
    }

    private fun saveNoteResult(result: SaveNoteResult): String? {
        return when (result) {
            is SaveNoteResult.Failure -> result.message

            SaveNoteResult.Success -> {
                // It doesn't return a string in case of success hence we mimic that that the check was successful
                "Success"
            }

            is SaveNoteResult.Warning -> result.message
        }
    }
}

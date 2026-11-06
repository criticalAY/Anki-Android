// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.clickRowAtPosition
import com.ichi2.anki.getBrowserWithNotes
import com.ichi2.anki.model.CardsOrNotes.CARDS
import com.ichi2.anki.multimedia.MultimediaResult
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import com.ichi2.anki.setCardBrowserFragmented
import com.ichi2.anki.setUsingSearchView
import com.ichi2.anki.waitForSearchResults
import com.ichi2.testutils.withSplitPaneUi
import kotlinx.coroutines.asCoroutineDispatcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class NoteEditorPendingImportNavigationTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `browser navigation offers to save a pending attachment`() {
        withSplitPaneUi {
            setCardBrowserFragmented(true)
            setUsingSearchView(false)
            val browser = getBrowserWithNotes(2)
            browser.viewModel.setCardsOrNotes(CARDS)
            advanceRobolectricLooper()
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            val editor = requireNotNull(browser.fragment)
            val originalRow = requireNotNull(browser.viewModel.paneRow)
            val noteId = col.getCard(originalRow.cardOrNoteId).nid
            val otherRow = browser.viewModel.cards.indexOfFirst { it != originalRow }
            check(otherRow >= 0)
            val field =
                AudioRecordingField().apply {
                    mediaFile = tempFolder.newFile("navigation.3gp").apply { writeBytes(ByteArray(256)) }
                }
            val executor = Executors.newSingleThreadExecutor()
            val releaseExecutor = Executors.newSingleThreadScheduledExecutor()
            val dispatcher = executor.asCoroutineDispatcher()
            val gate = CountDownLatch(1)
            executor.submit { check(gate.await(30, TimeUnit.SECONDS)) }
            val previous = CollectionManager.setTestDispatcher(dispatcher, useReentrantLock = false)
            try {
                editor.multimediaController.handleResult(MultimediaResult.Success(1, field))
                // Dirty checking can synchronously wait for the collection queue.
                releaseExecutor.schedule({ gate.countDown() }, 500, TimeUnit.MILLISECONDS)
                browser.clickRowAtPosition(otherRow)
                advanceRobolectricLooperUntil {
                    ShadowDialog.getLatestDialog()?.isShowing == true || browser.fragment !== editor
                }

                assertThat("the pending attachment requires a save decision", ShadowDialog.getLatestDialog()?.isShowing, equalTo(true))
                (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                advanceRobolectricLooperUntil { browser.fragment !== editor }
                assertThat(col.getNote(noteId).fields[1], containsString("[sound:navigation.3gp]"))
            } finally {
                gate.countDown()
                CollectionManager.setTestDispatcher(previous)
                dispatcher.close()
                releaseExecutor.shutdownNow()
                setCardBrowserFragmented(false)
            }
        }
    }
}

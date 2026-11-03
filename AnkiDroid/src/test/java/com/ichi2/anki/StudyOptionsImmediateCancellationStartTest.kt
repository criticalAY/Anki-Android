// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.view.View
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.databinding.FragmentStudyOptionsBinding
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.testutils.BackupManagerTestUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xlarge")
class StudyOptionsImmediateCancellationStartTest : RobolectricTest() {
    @Test
    fun `Start targets the displayed deck while the cancellation refresh is queued`() =
        checkStartAfterCancellation(deliverNotificationBeforeStart = true)

    @Test
    fun `Start targets the displayed deck before the cancellation notification is delivered`() =
        checkStartAfterCancellation(deliverNotificationBeforeStart = false)

    @Test
    fun `Start stays aligned after restoring a loading Custom Study dialog`() =
        checkStartAfterCancellation(deliverNotificationBeforeStart = false, recreate = true)

    private fun checkStartAfterCancellation(
        deliverNotificationBeforeStart: Boolean,
        recreate: Boolean = false,
    ) {
        val shown = addDeck("Shown", setAsSelected = true)
        val other = addDeck("Other")
        addNoteToDeck(shown)
        addNoteToDeck(other)
        col.decks.select(shown)

        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        run {
            var picker = controller.get()
            advanceRobolectricLooperUntil {
                picker.viewModel.loadDeckCounts?.isCompleted == true && picker.fragment?.shownDeckId == shown
            }
            advanceRobolectricLooper()
            var pane = assertNotNull(picker.fragment)
            col.decks.select(other)
            runBlocking { withContext(Dispatchers.Default) { pane.viewModel.refreshData() } }
            assertEquals(other, pane.viewModel.selectedDeckId)
            assertEquals(shown, pane.shownDeckId)

            val collection = col
            val ioScheduler = TestCoroutineScheduler()
            val collectionScheduler = TestCoroutineScheduler()
            val previousIo = ioDispatcher
            ioDispatcher = StandardTestDispatcher(ioScheduler)
            val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(collectionScheduler), useReentrantLock = false)
            try {
                pane.onMenuItemSelected(MenuBuilder(picker).add(0, R.id.action_custom_study, 0, "Custom Study"))
                picker.supportFragmentManager.executePendingTransactions()
                var dialog =
                    picker.supportFragmentManager.fragments
                        .filterIsInstance<CustomStudyDialog>()
                        .single()
                advanceRobolectricLooper()
                assertEquals(other, pane.shownDeckId)

                if (recreate) {
                    controller.recreate()
                    picker = controller.get()
                    repeat(10) {
                        collectionScheduler.runCurrent()
                        advanceRobolectricLooper()
                    }
                    pane = assertNotNull(picker.fragment)
                    dialog =
                        picker.supportFragmentManager.fragments
                            .filterIsInstance<CustomStudyDialog>()
                            .single()
                    assertEquals(other, pane.shownDeckId)
                    assertEquals(other, collection.decks.selected())
                }

                // Commit selection on the real collection path, but leave its return to IO queued.
                ioScheduler.runCurrent()
                collectionScheduler.runCurrent()
                assertEquals(dialog.viewModel.deckId, collection.decks.selected())
                assertTrue(assertNotNull(dialog.requireDialog().findViewById<View>(R.id.progress_bar)).isVisible)
                dialog.requireDialog().cancel()
                picker.supportFragmentManager.executePendingTransactions()

                // The notification or the slow collection refresh may still be queued when Start is tapped.
                if (deliverNotificationBeforeStart) advanceRobolectricLooper()
                assertEquals(other, pane.shownDeckId)

                val displayed = assertNotNull(pane.shownDeckId)
                val start = FragmentStudyOptionsBinding.bind(pane.requireView()).studyoptionsStart
                // A real tap is possible only while Start is enabled; waiting for the refresh is safe too.
                if (start.isEnabled) {
                    assertTrue(start.performClick())
                    assertEquals(Reviewer::class.java.name, assertNotNull(shadowOf(picker).nextStartedActivity).component?.className)
                    assertEquals(displayed, collection.decks.selected(), "Start must study the deck displayed after cancellation")
                }
                repeat(10) {
                    ioScheduler.runCurrent()
                    collectionScheduler.runCurrent()
                    advanceRobolectricLooper()
                }
                assertEquals(collection.decks.selected(), pane.shownDeckId)
                assertTrue(start.isEnabled, "Start must become available again after the selection refresh")
            } finally {
                repeat(10) {
                    ioScheduler.runCurrent()
                    collectionScheduler.runCurrent()
                    advanceRobolectricLooper()
                }
                CollectionManager.setTestDispatcher(previousQueue)
                ioDispatcher = previousIo
            }
        }
    }
}

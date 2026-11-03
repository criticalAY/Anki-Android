// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.view.menu.MenuBuilder
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.databinding.FragmentStudyOptionsBinding
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xlarge")
class StudyOptionsStartAfterCustomStudyTest : RobolectricTest() {
    @Test
    fun `Start waits when Custom Study closes before the redraw`() =
        withPanel { picker, panel, shown, other ->
            col.decks.select(other)
            runBlocking { withContext(Dispatchers.Default) { panel.viewModel.refreshData() } }
            assertEquals(shown, panel.shownDeckId)
            val collection = col

            withHeldQueues { runQueues ->
                panel.onMenuItemSelected(MenuBuilder(picker).add(0, R.id.action_custom_study, 0, "Custom Study"))
                val dialog = picker.customStudyDialog()
                runQueues()
                assertEquals(dialog.viewModel.deckId, collection.decks.selected())

                dialog.requireDialog().cancel()
                advanceRobolectricLooper()

                assertEquals(other, panel.shownDeckId)
                assertStartMatchesPanel(panel, collection)
                settle(runQueues)
                assertSettled(panel, collection)
            }
        }

    @Test
    fun `Start waits after Custom Study from the deck list`() =
        withPanel { picker, panel, shown, other ->
            val collection = col

            withHeldQueues { runQueues ->
                picker.selectContextMenuOptionAndAwait(DeckPickerContextMenuOption.CUSTOM_STUDY, other) {
                    picker.supportFragmentManager.fragments.any { it is CustomStudyDialog }
                }
                val dialog = picker.customStudyDialog()
                runQueues()
                assertEquals(other, collection.decks.selected())

                dialog.requireDialog().cancel()

                assertEquals(shown, panel.shownDeckId)
                assertStartMatchesPanel(panel, collection)
                settle(runQueues)
                assertSettled(panel, collection)
            }
        }

    private fun withPanel(block: (DeckPicker, StudyOptionsFragment, DeckId, DeckId) -> Unit) {
        val shown = addDeck("Shown", setAsSelected = true)
        val other = addDeck("Other")
        addNoteToDeck(shown)
        addNoteToDeck(other)
        col.decks.select(shown)

        withDeckPicker(deckCount = 0) { picker ->
            advanceRobolectricLooperUntil {
                picker.viewModel.loadDeckCounts?.isCompleted == true && picker.fragment?.shownDeckId == shown
            }
            advanceRobolectricLooper()
            block(picker, assertNotNull(picker.fragment), shown, other)
        }
    }

    private fun withHeldQueues(block: (runQueues: () -> Unit) -> Unit) {
        val io = TestCoroutineScheduler()
        val queue = TestCoroutineScheduler()
        val runQueues = {
            io.runCurrent()
            queue.runCurrent()
        }
        val previousIo = ioDispatcher
        ioDispatcher = StandardTestDispatcher(io)
        val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(queue), useReentrantLock = false)
        try {
            block(runQueues)
        } finally {
            settle(runQueues)
            CollectionManager.setTestDispatcher(previousQueue)
            ioDispatcher = previousIo
        }
    }

    private fun settle(runQueues: () -> Unit) =
        repeat(10) {
            runQueues()
            advanceRobolectricLooper()
        }

    private fun DeckPicker.customStudyDialog(): CustomStudyDialog {
        supportFragmentManager.executePendingTransactions()
        return supportFragmentManager.fragments
            .filterIsInstance<CustomStudyDialog>()
            .single()
    }

    private fun assertStartMatchesPanel(
        panel: StudyOptionsFragment,
        collection: Collection,
    ) = assertTrue(
        !panel.start.isEnabled || panel.shownDeckId == collection.decks.selected(),
        "Start is on while the panel shows another deck",
    )

    private fun assertSettled(
        panel: StudyOptionsFragment,
        collection: Collection,
    ) {
        assertEquals(collection.decks.selected(), panel.shownDeckId)
        assertTrue(panel.start.isEnabled)
    }

    private val StudyOptionsFragment.start
        get() = FragmentStudyOptionsBinding.bind(requireView()).studyoptionsStart
}

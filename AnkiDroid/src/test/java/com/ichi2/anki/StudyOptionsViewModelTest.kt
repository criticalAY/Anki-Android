/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import app.cash.turbine.test
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.testutils.ensureOpsExecuted
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.instanceOf
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class StudyOptionsViewModelTest : RobolectricTest() {
    private val viewModel = StudyOptionsViewModel()

    @Test
    fun `initial state is Loading`() {
        assertThat(viewModel.state, instanceOf(StudyOptionsState.Loading::class.java))
    }

    @Test
    fun `refreshData - empty deck shows Empty state`() =
        runTest {
            col
            viewModel.refreshData().join()
            assertIs<StudyOptionsState.Empty>(viewModel.state)
        }

    @Test
    fun `refreshData - deck with due cards shows StudyOptions state`() =
        runTest {
            addBasicNote("Front", "Back")

            viewModel.refreshData().join()

            val state = viewModel.state
            assertIs<StudyOptionsState.StudyOptions>(state)
            assertEquals(1, state.data.newCardsToday)
            assertEquals(1, state.data.numberOfCardsInDeck)
            assertEquals(1, state.data.totalNewCards)
            assertEquals(0, state.data.lrnCardsToday)
            assertEquals(0, state.data.revCardsToday)
        }

    @Test
    fun `refreshData - regular deck is not filtered`() =
        runTest {
            addBasicNote()

            viewModel.refreshData().join()

            assertFalse(viewModel.isFilteredDeck)
        }

    @Test
    fun `refreshData - congrats state when no cards due`() =
        runTest {
            addBasicNote()
            withCol {
                while (sched.card != null) {
                    val card = sched.card!!
                    sched.answerCard(card, Rating.EASY)
                }
            }

            viewModel.refreshData().join()

            assertIs<StudyOptionsState.Congrats>(viewModel.state)
        }

    @Test
    fun `refreshData - state flow emits updates`() =
        runTest {
            col
            viewModel.flowOfState.test {
                assertIs<StudyOptionsState.Loading>(awaitItem())

                viewModel.refreshData().join()
                assertIs<StudyOptionsState.Empty>(awaitItem())

                addBasicNote()
                viewModel.refreshData().join()
                assertIs<StudyOptionsState.StudyOptions>(awaitItem())
            }
        }

    @Test
    fun `refreshData - multiple cards counted correctly`() =
        runTest {
            repeat(5) { addBasicNote("Front $it", "Back $it") }

            viewModel.refreshData().join()

            val state = assertIs<StudyOptionsState.StudyOptions>(viewModel.state)
            assertEquals(5, state.data.newCardsToday)
            assertEquals(5, state.data.numberOfCardsInDeck)
        }

    @Test
    fun `refreshData - deck name is correct`() =
        runTest {
            addBasicNote()

            viewModel.refreshData().join()

            val state = assertIs<StudyOptionsState.StudyOptions>(viewModel.state)
            assertEquals("Default", state.deckName)
        }

    @Test
    fun `rebuildCram - updates state`() =
        runTest {
            addBasicNote()
            addDynamicDeck("Filtered", "")

            viewModel.rebuildCram()

            val state = viewModel.state
            assertThat(state, instanceOf(StudyOptionsState::class.java))
            assertTrue(viewModel.isFilteredDeck)
        }

    @Test
    fun `emptyCram - is undoable`() =
        runTest {
            addBasicNote()
            addDynamicDeck("Filtered", "")

            ensureOpsExecuted(1) {
                viewModel.emptyCram()
            }
        }

    @Test
    fun `rebuildCram - is undoable`() =
        runTest {
            addBasicNote()
            addDynamicDeck("Filtered", "")

            ensureOpsExecuted(1) {
                viewModel.rebuildCram()
            }
        }

    @Test
    fun `unbury - is undoable`() =
        runTest {
            addBasicNote()
            withCol {
                val card = sched.card!!
                sched.buryCards(listOf(card.id), true)
            }

            ensureOpsExecuted(1) {
                viewModel.unbury().join()
            }
        }

    @Test
    fun `haveBuried - false when no buried cards`() =
        runTest {
            addBasicNote()

            viewModel.refreshData().join()

            assertFalse(viewModel.haveBuried)
        }

    @Test
    fun `refreshData - buried cards are counted`() =
        runTest {
            addBasicNote("Front1", "Back1")
            addBasicNote("Front2", "Back2")
            withCol {
                val card = sched.card!!
                sched.buryCards(listOf(card.id), true)
            }

            viewModel.refreshData().join()

            val state = assertIs<StudyOptionsState.StudyOptions>(viewModel.state)
            assertTrue(state.data.buriedNew > 0, "expected buried new cards")
            assertEquals(1, state.data.newCardsToday)
        }
}

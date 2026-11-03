// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Looper.getMainLooper
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.ImageButton
import android.widget.Spinner
import androidx.activity.ComponentDialog
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.testing.launchFragment
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog.Companion.ARG_NOTE_IDS
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.progress.ViewModelProgress
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.testutils.EmptyAnkiActivity
import com.ichi2.testutils.withCollectionAccessOn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import kotlin.coroutines.CoroutineContext
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class ChangeNoteTypeDialogTest : RobolectricTest() {
    @Test
    fun `no changes failure has a translated message`() {
        assertThat(ChangeNoteTypeException.Kind.NO_CHANGES.toString(targetContext), equalTo("No changes to save"))
    }

    /**
     * #20944: with an accessibility service enabled, a Spinner reports its initial selection.
     * A template selection is meaningless for cloze, and used to be fatal
     */
    @Test
    fun `templates tab for a cloze note does not crash with an accessibility service`() {
        enableAccessibilityService()

        withChangeNoteTypeDialog(addClozeNote("{{c1::Hi}}").id) {
            selectTemplatesTab()
        }
        // a crash is reported by FailOnUnhandledExceptionRule
    }

    /** #20944: spinners which can't be used still deliver selections, which used to be fatal */
    @Test
    fun `no template spinners are built for a cloze note type`() =
        withChangeNoteTypeDialog(addClozeNote("{{c1::Hi}}").id) {
            assertThat("templates are locked", templatesContainer().childCount, equalTo(0))
        }

    @Test
    fun `template spinners are built for a regular note type`() =
        withChangeNoteTypeDialog(addBasicNote().id) {
            assertThat("a spinner per output template", templatesContainer().childCount, equalTo(1))
        }

    @Test
    fun `template spinners are rebuilt after visiting cloze`() =
        withChangeNoteTypeDialog(addBasicNote().id) {
            val container = templatesContainer()

            selectNoteType("Cloze")
            selectNoteType("Basic (and reversed card)")

            assertThat("shown for regular -> regular", container.visibility, equalTo(View.VISIBLE))
            assertThat("one spinner per output template", container.childCount, equalTo(2))
        }

    @Test
    fun `a loading dialog shows while the note type is changing`() =
        runTest {
            val note = addBasicNote("Hello", "World")
            val dialog = showChangeNoteTypeDialog(note.id)
            val fragmentManager = dialog.parentFragmentManager
            val viewModel = dialog.swapFields()
            val queue = HeldCollectionQueue()

            withCollectionAccessOn(queue) {
                onView(withId(R.id.btn_save)).inRoot(isDialog()).perform(click())
                queue.runUntilChanging(viewModel)
                advanceUntilIdle()
                advanceRobolectricLooper()
                assertThat("loading dialog is shown", fragmentManager.loadingDialogCount(), equalTo(1))

                queue.runAll()
            }
            advanceUntilIdle()
            advanceRobolectricLooper()

            assertThat("loading dialog is dismissed", fragmentManager.loadingDialogCount(), equalTo(0))
            assertThat("dialog is closed", dialog.isAdded, equalTo(false))
            assertThat("fields are swapped", col.getNote(note.id).fields, equalTo(listOf("World", "Hello")))
        }

    @Test
    fun `tapping Save twice changes the note type once`() =
        runTest {
            val note = addBasicNote("Hello", "World")
            showChangeNoteTypeDialog(note.id).swapFields()
            onView(withId(R.id.btn_save)).inRoot(isDialog()).check(matches(isEnabled()))

            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                repeat(2) {
                    onView(withId(R.id.btn_save)).inRoot(isDialog()).perform(click())
                }
            }
            advanceUntilIdle()

            assertThat("fields are swapped once", col.getNote(note.id).fields, equalTo(listOf("World", "Hello")))
        }

    @Test
    fun `tapping Save after accepting a one-way sync changes the note type once`() =
        runTest {
            val note = addBasicNote("Hello", "World")
            col.db.execute("update col set ls = scm")
            showChangeNoteTypeDialog(note.id).swapFields()
            onView(withId(R.id.btn_save)).inRoot(isDialog()).perform(click())
            advanceUntilIdle()
            val oneWaySyncPrompt = ShadowDialog.getLatestDialog() as AlertDialog

            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                oneWaySyncPrompt.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                shadowOf(getMainLooper()).idle()
                onView(withId(R.id.btn_save)).inRoot(isDialog()).perform(click())
            }
            advanceUntilIdle()

            assertThat("fields are swapped once", col.getNote(note.id).fields, equalTo(listOf("World", "Hello")))
        }

    @Test
    fun `Save asks for a one-way sync again after it is declined`() =
        runTest {
            val note = addBasicNote("Hello", "World")
            col.db.execute("update col set ls = scm")
            showChangeNoteTypeDialog(note.id).swapFields()
            onView(withId(R.id.btn_save)).inRoot(isDialog()).perform(click())
            advanceUntilIdle()
            val declinedPrompt = ShadowDialog.getLatestDialog() as AlertDialog
            declinedPrompt.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
            advanceRobolectricLooper()

            onView(withId(R.id.btn_save)).inRoot(isDialog()).perform(click())
            advanceUntilIdle()

            val latestDialog = ShadowDialog.getLatestDialog()
            assertThat("a new prompt is shown", latestDialog is AlertDialog && latestDialog !== declinedPrompt, equalTo(true))
            assertThat("the prompt is showing", latestDialog.isShowing, equalTo(true))
            assertThat("fields are unchanged", col.getNote(note.id).fields, equalTo(listOf("Hello", "World")))
        }

    @Test
    fun `tapping Save in a recreated dialog while the note type is changing changes it once`() =
        runTest {
            val note = addBasicNote("Hello", "World")
            val controller = startActivityControllerNormallyOpenCollectionWithIntent(EmptyAnkiActivity::class.java, null)
            val viewModel = showChangeNoteTypeDialog(note.id, controller.get()).swapFields()
            val queue = HeldCollectionQueue()

            withCollectionAccessOn(queue) {
                onView(withId(R.id.btn_save)).inRoot(isDialog()).perform(click())
                queue.runUntilChanging(viewModel)
                controller.recreate()
                controller.get().setContentView(CoordinatorLayout(controller.get()).apply { id = R.id.root_layout })
                advanceRobolectricLooper()

                onView(withId(R.id.btn_save)).inRoot(isDialog()).perform(click())
                queue.runAll()
            }
            advanceUntilIdle()

            assertThat("fields are swapped once", col.getNote(note.id).fields, equalTo(listOf("World", "Hello")))
        }

    @Test
    fun `back while the note type is changing does not close the dialog`() =
        runTest {
            val note = addBasicNote("Hello", "World")
            val dialog = showChangeNoteTypeDialog(note.id)
            dialog.swapFields()

            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                onView(withId(R.id.btn_save)).inRoot(isDialog()).perform(click())
                (dialog.requireDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
                shadowOf(getMainLooper()).idle()
                assertThat("dialog is open", dialog.isAdded, equalTo(true))
            }
            advanceUntilIdle()

            assertThat("fields are swapped", col.getNote(note.id).fields, equalTo(listOf("World", "Hello")))
        }

    @Test
    fun `closing while the note type is changing does not close the dialog`() =
        runTest {
            val note = addBasicNote("Hello", "World")
            val dialog = showChangeNoteTypeDialog(note.id)
            dialog.swapFields()

            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                onView(withId(R.id.btn_save)).inRoot(isDialog()).perform(click())
                onView(allOf(isDescendantOfA(withId(R.id.toolbar)), isAssignableFrom(ImageButton::class.java)))
                    .inRoot(isDialog())
                    .perform(click())
                shadowOf(getMainLooper()).idle()
                assertThat("dialog is open", dialog.isAdded, equalTo(true))
            }
            advanceUntilIdle()

            assertThat("fields are swapped", col.getNote(note.id).fields, equalTo(listOf("World", "Hello")))
        }

    @Test
    fun `back closes the dialog when the note type is not changing`() =
        runTest {
            val dialog = showChangeNoteTypeDialog(addBasicNote().id)

            (dialog.requireDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
            advanceRobolectricLooper()

            assertThat("dialog is closed", dialog.isAdded, equalTo(false))
        }

    @Test
    fun `the close button closes the dialog when the note type is not changing`() =
        runTest {
            val dialog = showChangeNoteTypeDialog(addBasicNote().id)

            onView(allOf(isDescendantOfA(withId(R.id.toolbar)), isAssignableFrom(ImageButton::class.java)))
                .inRoot(isDialog())
                .perform(click())
            advanceRobolectricLooper()

            assertThat("dialog is closed", dialog.isAdded, equalTo(false))
        }

    private class HeldCollectionQueue : CoroutineDispatcher() {
        val pending = ArrayDeque<Runnable>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            pending.addLast(block)
        }
    }

    private fun HeldCollectionQueue.runUntilChanging(viewModel: ChangeNoteTypeViewModel) {
        while (viewModel.progressManager.progress.value !is ViewModelProgress.Active) {
            pending.removeFirst().run()
            advanceRobolectricLooper()
        }
    }

    private fun HeldCollectionQueue.runAll() {
        while (pending.isNotEmpty()) {
            pending.removeFirst().run()
        }
    }

    private fun FragmentManager.loadingDialogCount() = fragments.count { it is LoadingDialogFragment }

    private fun showChangeNoteTypeDialog(
        noteId: NoteId,
        activity: EmptyAnkiActivity = startRegularActivity(),
    ): ChangeNoteTypeDialog {
        activity.setContentView(CoordinatorLayout(activity).apply { id = R.id.root_layout })
        val dialog = ChangeNoteTypeDialog.newInstance(listOf(noteId))
        activity.showDialogFragment(dialog)
        shadowOf(getMainLooper()).idle()
        return dialog
    }

    private suspend fun ChangeNoteTypeDialog.swapFields(): ChangeNoteTypeViewModel {
        val viewModel = ViewModelProvider(this)[ChangeNoteTypeViewModel::class.java]
        viewModel.updateFieldMapping(0, SelectedIndex.from(1)).join()
        viewModel.updateFieldMapping(1, SelectedIndex.from(0)).join()
        shadowOf(getMainLooper()).idle()
        return viewModel
    }

    /** The template mapping rows, which only apply to a regular -> regular conversion */
    private fun ChangeNoteTypeDialog.templatesContainer(): ViewGroup {
        // the templates tab is the second page of the pager
        val pager = requireView().findViewById<View>(R.id.change_note_type_pager)
        assertThat("the dialog has loaded", pager.visibility, equalTo(View.VISIBLE))
        selectTemplatesTab()
        return requireNotNull(requireView().findViewById(R.id.templates_container)) {
            "templates_container was not created"
        }
    }

    private fun ChangeNoteTypeDialog.selectTemplatesTab() {
        requireView()
            .findViewById<ViewPager2>(R.id.change_note_type_pager)
            .setCurrentItem(ChangeNoteTypeViewModel.Tab.Templates.position, false)
        shadowOf(getMainLooper()).idle()
    }

    /** Picks a note type in the 'new note type' spinner, as a user would */
    private fun ChangeNoteTypeDialog.selectNoteType(name: String) {
        val spinner = requireView().findViewById<Spinner>(R.id.dest_note_type_spinner)
        val id = requireNotNull(col.notetypes.byName(name)) { "note type '$name' not found" }.id
        val position = (0 until spinner.count).first { spinner.adapter.getItemId(it) == id }
        spinner.setSelection(position)
        shadowOf(getMainLooper()).idle()
    }

    /** Makes [AccessibilityManager.isEnabled] return true, as it would with TalkBack running */
    private fun enableAccessibilityService() {
        val manager = targetContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        shadowOf(manager).setEnabled(true)
    }

    private fun withChangeNoteTypeDialog(
        noteId: NoteId,
        action: ChangeNoteTypeDialog.() -> Unit,
    ) {
        launchFragment<ChangeNoteTypeDialog>(
            fragmentArgs = Bundle().apply { putLongArray(ARG_NOTE_IDS, longArrayOf(noteId)) },
            themeResId = CommonR.style.Theme_Light,
        ).use { scenario -> scenario.onFragment { action(it) } }
    }
}

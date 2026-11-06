// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.multimedia.MultimediaResult
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import io.mockk.every
import io.mockk.mockkObject
import kotlinx.coroutines.asCoroutineDispatcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.fakes.RoboMenuItem
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class NoteEditorRapidImportSaveTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `rapid saves during media import do not discard ordinary edits`() {
        ensureCollectionLoadIsSynchronous()
        val note = addBasicNote("Front", "Back")
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            )
        val editor = controller.get().getNoteEditorFragment()
        editor.setField(0, "Edited front")
        advanceRobolectricLooper()
        val tags = col.tags
        val field =
            AudioRecordingField().apply {
                mediaFile = tempFolder.newFile("rapid.3gp").apply { writeBytes(ByteArray(256)) }
            }
        val executor = Executors.newSingleThreadExecutor()
        val releaseExecutor = Executors.newSingleThreadScheduledExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val importGate = CountDownLatch(1)
        val writeGate = CountDownLatch(1)
        val writeStarted = CountDownLatch(1)
        val writeEnded = AtomicBoolean(false)
        val blockNextWrite = AtomicBoolean(true)
        executor.submit { check(importGate.await(30, TimeUnit.SECONDS)) }
        mockkObject(tags) {
            every { tags.split(any()) } answers {
                val result = callOriginal()
                // Queue another operation after synchronous save preparation and before its write.
                if (blockNextWrite.getAndSet(false)) {
                    executor.submit {
                        writeStarted.countDown()
                        try {
                            check(writeGate.await(30, TimeUnit.SECONDS))
                        } finally {
                            writeEnded.set(true)
                        }
                    }
                }
                result
            }
            val previous = CollectionManager.setTestDispatcher(dispatcher, useReentrantLock = false)
            try {
                editor.multimediaController.handleResult(MultimediaResult.Success(1, field))
                // The pre-PR save blocks synchronously; release independently of the UI thread.
                releaseExecutor.schedule({ importGate.countDown() }, 500, TimeUnit.MILLISECONDS)
                repeat(2) {
                    val touchesBlocked =
                        controller
                            .get()
                            .window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0
                    if (!touchesBlocked) editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
                }
                advanceRobolectricLooperUntil { writeStarted.count == 0L }
                assertThat("the database write is still queued", writeEnded.get(), equalTo(false))
                if (controller.get().isFinishing) controller.pause().stop().destroy()
                writeGate.countDown()
                executor.submit {}.get(10, TimeUnit.SECONDS)
                advanceRobolectricLooperUntil { controller.get().isFinishing }

                assertThat("ordinary edits must be persisted", col.getNote(note.id).fields[0], equalTo("Edited front"))
            } finally {
                importGate.countDown()
                writeGate.countDown()
                CollectionManager.setTestDispatcher(previous)
                dispatcher.close()
                releaseExecutor.shutdownNow()
            }
        }
    }
}

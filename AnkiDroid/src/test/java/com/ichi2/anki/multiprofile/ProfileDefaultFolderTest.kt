// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multiprofile

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Environment
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.AnkiDroidFolder
import com.ichi2.anki.common.storage.CollectionHelper.PREF_COLLECTION_PATH
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.selectAnkiDroidFolder
import com.ichi2.anki.startup.getDefaultAnkiDroidDirectory
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.EmptyAnkiActivity
import kotlinx.coroutines.test.TestScope
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ProfileDefaultFolderTest : RobolectricTest() {
    @Test
    fun `a named profile's default folder is its own`() =
        withWorkProfile { _, workFolder ->
            assertEquals(workFolder, getDefaultAnkiDroidDirectory(startActivity()).absolutePath)
        }

    @Test
    fun `a profile on shared storage still defaults to its own folder`() =
        withWorkProfile { workPreferences, workFolder ->
            ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
            ShadowEnvironment.setIsExternalStorageLegacy(true)
            @Suppress("DEPRECATION")
            val sharedFolder = File(Environment.getExternalStorageDirectory(), "Work")
            workPreferences.edit(commit = true) { putString(PREF_COLLECTION_PATH, sharedFolder.absolutePath) }
            val activity = startActivity()
            assertEquals(AnkiDroidFolder.PUBLIC, selectAnkiDroidFolder(activity))

            assertEquals(workFolder, getDefaultAnkiDroidDirectory(activity).absolutePath)
        }

    @Test
    fun `Use default folder keeps a profile in its own folder`() =
        withWorkProfile { workPreferences, workFolder ->
            workPreferences.edit(commit = true) { putString(PREF_COLLECTION_PATH, tempFolder.newFolder().absolutePath) }
            val activity = startActivity()
            activity.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_ERROR_HANDLING)

            activity.errorDialog().clickItem(getResourceString(CommonString.backup_use_default_folder))

            assertEquals(workFolder, workPreferences.getString(PREF_COLLECTION_PATH, null))
        }

    private fun withWorkProfile(block: TestScope.(workPreferences: SharedPreferences, workFolder: String?) -> Unit) =
        runTest {
            val app = AnkiDroidApp.instance
            val running = requireNotNull(app.profileManager)
            val base =
                object : ContextWrapper(app.baseContext) {
                    override fun getApplicationContext(): Context? = null
                }
            val work = running.createNewProfile((ProfileName.validate("Work") as ProfileName.ValidationResult.Valid).name)
            assertEquals(ProfileSwitchGuard.Result.Success, ProfileSwitchGuard(running, emptyList())(work))
            val workManager = ProfileManager.create(base)
            app.profileManager = workManager
            try {
                val workPreferences = workManager.activeProfileContext.sharedPrefs()
                block(workPreferences, workPreferences.getString(PREF_COLLECTION_PATH, null))
            } finally {
                app.profileManager = running
            }
        }

    private fun startActivity(): EmptyAnkiActivity =
        Robolectric
            .buildActivity(EmptyAnkiActivity::class.java)
            .setup()
            .also(::saveControllerForCleanup)
            .get()

    private fun EmptyAnkiActivity.errorDialog(): AlertDialog =
        (supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as DatabaseErrorDialog).requireDialog() as AlertDialog

    private fun AlertDialog.clickItem(label: String) {
        val position = (0 until listView.count).first { listView.getItemAtPosition(it) == label }
        listView.performItemClick(null, position, listView.getItemIdAtPosition(position))
        advanceRobolectricLooper()
    }
}

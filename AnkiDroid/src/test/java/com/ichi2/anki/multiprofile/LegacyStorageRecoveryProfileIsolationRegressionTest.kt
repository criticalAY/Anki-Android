// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multiprofile

import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import android.os.Environment
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.AnkiDroidFolder
import com.ichi2.anki.common.storage.CollectionHelper.PREF_COLLECTION_PATH
import com.ichi2.anki.dialogs.DatabaseErrorDialog.UninstallListItem
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.anki.startup.getDefaultAnkiDroidDirectory
import com.ichi2.testutils.EmptyAnkiActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class LegacyStorageRecoveryProfileIsolationRegressionTest : RobolectricTest() {
    @Test
    fun `new collection stays in the profile`() =
        runTest {
            val app = AnkiDroidApp.instance
            val running = requireNotNull(app.profileManager)
            val base =
                object : ContextWrapper(app.baseContext) {
                    override fun getApplicationContext(): Context? = null
                }
            val defaultDirectory = getDefaultAnkiDroidDirectory(base, folder = AnkiDroidFolder.APP_PRIVATE)
            base.sharedPrefs().edit(commit = true) { putString(PREF_COLLECTION_PATH, defaultDirectory.absolutePath) }
            val work = running.createNewProfile((ProfileName.validate("Work") as ProfileName.ValidationResult.Valid).name)
            assertEquals(ProfileSwitchGuard.Result.Success, ProfileSwitchGuard(running, emptyList())(work))

            val workManager = ProfileManager.create(base)
            app.profileManager = workManager
            try {
                val workPreferences = workManager.activeProfileContext.sharedPrefs()
                val workDirectory = workPreferences.getString(PREF_COLLECTION_PATH, null)
                assertNotEquals(defaultDirectory.absolutePath, workDirectory)

                ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
                ShadowEnvironment.setIsExternalStorageLegacy(false)
                @Suppress("DEPRECATION")
                val publicDirectory = File(Environment.getExternalStorageDirectory(), "previous-work-collection")
                workPreferences.edit(commit = true) { putString(PREF_COLLECTION_PATH, publicDirectory.absolutePath) }
                val host =
                    Robolectric
                        .buildActivity(EmptyAnkiActivity::class.java)
                        .setup()
                        .also(::saveControllerForCleanup)
                        .get()
                assertEquals(publicDirectory.absolutePath, host.sharedPrefs().getString(PREF_COLLECTION_PATH, null))
                assertTrue(ScopedStorageService.collectionWasMadeInaccessibleAfterUninstall(host))

                UninstallListItem.RECREATE_COLLECTION.onClick(host)
                val confirmation = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
                assertTrue(confirmation.getButton(DialogInterface.BUTTON_POSITIVE).performClick())
                advanceRobolectricLooper()

                assertEquals(workDirectory, workPreferences.getString(PREF_COLLECTION_PATH, null))
                assertEquals(defaultDirectory.absolutePath, base.sharedPrefs().getString(PREF_COLLECTION_PATH, null))
            } finally {
                app.profileManager = running
            }
        }
}

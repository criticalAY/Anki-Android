// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multiprofile

import android.content.Context
import android.content.ContextWrapper
import android.os.Environment
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.AnkiDroidFolder
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.storage.CollectionHelper.PREF_COLLECTION_PATH
import com.ichi2.anki.dialogs.DatabaseErrorDialog.UninstallListItem
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.anki.startup.getDefaultAnkiDroidDirectory
import com.ichi2.testutils.EmptyAnkiActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NamedProfileBackupRestoreIsolationTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `restore leaves Default's collection alone`() =
        runTest {
            val app = AnkiDroidApp.instance
            val base =
                object : ContextWrapper(app.baseContext) {
                    override fun getApplicationContext(): Context? = null
                }
            val defaultProfile = requireNotNull(app.profileManager)
            val defaultDirectory = getDefaultAnkiDroidDirectory(base, folder = AnkiDroidFolder.APP_PRIVATE)
            base.sharedPrefs().edit(commit = true) { putString(PREF_COLLECTION_PATH, defaultDirectory.absolutePath) }
            CollectionManager.closeCollectionBlocking()
            assertEquals(defaultDirectory, CollectionManager.getCollectionDirectory())

            addBasicNote("backed up note", "answer")
            val backupDirectory = tempFolder.newFolder("backups")
            assertTrue(col.createBackup(backupDirectory.path, force = true, waitForCompletion = true))
            val backup = assertNotNull(backupDirectory.listFiles()).single { it.extension == "colpkg" }
            addBasicNote("note added after backup", "answer")
            assertEquals(2, col.noteCount())
            CollectionManager.closeCollectionBlocking()

            val work = defaultProfile.createNewProfile((ProfileName.validate("Work") as ProfileName.ValidationResult.Valid).name)
            assertEquals(ProfileSwitchGuard.Result.Success, ProfileSwitchGuard(defaultProfile, emptyList())(work))
            val workManager = ProfileManager.create(base)
            app.profileManager = workManager
            val previousOverride = CollectionHelper.ankiDroidDirectoryOverride
            try {
                ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
                ShadowEnvironment.setIsExternalStorageLegacy(false)
                @Suppress("DEPRECATION")
                val publicDirectory = File(Environment.getExternalStorageDirectory(), "previous-work-collection")
                workManager.activeProfileContext.sharedPrefs().edit(
                    commit = true,
                ) { putString(PREF_COLLECTION_PATH, publicDirectory.absolutePath) }
                val host =
                    Robolectric
                        .buildActivity(EmptyAnkiActivity::class.java)
                        .setup()
                        .also(::saveControllerForCleanup)
                        .get()
                assertEquals(publicDirectory.absolutePath, host.sharedPrefs().getString(PREF_COLLECTION_PATH, null))
                assertTrue(ScopedStorageService.collectionWasMadeInaccessibleAfterUninstall(host))

                UninstallListItem.RESTORE_FROM_BACKUP.onClick(host)
                advanceRobolectricLooperUntil { CollectionHelper.ankiDroidDirectoryOverride != null }
                CollectionManager.importColpkg(backup.path)
                assertNotNull(host.importColpkgListener).onImportColpkg(backup.path)
                assertEquals(1, col.noteCount(), "The backup should have been restored into Work")
            } finally {
                CollectionManager.closeCollectionBlocking()
                CollectionHelper.ankiDroidDirectoryOverride = previousOverride
                app.profileManager = defaultProfile
            }

            assertEquals(2, col.noteCount(), "Restoring Work must preserve Default's newer note")
        }
}

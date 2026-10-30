// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PREF_LAST_ACTIVE_PROFILE_ID
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PROFILE_REGISTRY_FILENAME
import com.ichi2.anki.multiprofile.ProfileName
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class DeckPickerProfileLoadFailureTest : RobolectricTest() {
    private val registry
        get() = targetContext.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)

    private val profileFailureDialogs: List<AlertDialog>
        get() =
            ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().filter {
                it.title == targetContext.getString(CommonString.profile_load_failed_title, "Work")
            }

    @After
    fun resetProfileFailure() {
        ProfileManager.resetForTesting()
    }

    @Test
    fun `startup explains that a profile could not be opened`() {
        failToOpenProfile()

        deckPicker {
            assertEquals(1, profileFailureDialogs.size)
        }
    }

    @Test
    fun `Use Default opens the Default profile from the next start`() {
        failToOpenProfile()

        deckPicker {
            profileFailureDialogs.single().getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
            advanceRobolectricLooper()

            assertEquals(ProfileId.DEFAULT.value, registry.getString(PREF_LAST_ACTIVE_PROFILE_ID, null))
        }
    }
}

fun RobolectricTest.failToOpenProfile() {
    val work = ProfileManager.create(targetContext).createNewProfile(ProfileName.fromTrustedSource("Work"))
    File(targetContext.filesDir.parentFile, work.value).writeText("a file where the profile folder belongs")
    targetContext.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE).edit(commit = true) {
        putString(PREF_LAST_ACTIVE_PROFILE_ID, work.value)
    }
    assertNull(ProfileManager.createOrNull(targetContext))
}

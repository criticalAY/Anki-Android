// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.fail

class ShortStringLengthTest {
    private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

    @Test
    fun `short strings are shorter than the strings they replace in every language`() {
        val failures = mutableListOf<String>()
        for (dir in resourceDirs()) {
            val strings = stringsIn(dir)
            for ((name, shortText) in strings.filterKeys { it.endsWith(SHORT_SUFFIX) }) {
                val replacedName = name.removeSuffix(SHORT_SUFFIX)
                val replacedText = strings[replacedName]
                when {
                    replacedText == null && dir.name == "values" -> failures.add("'$name' has no '$replacedName' to replace")
                    replacedText != null && shortText.length >= replacedText.length ->
                        failures.add("${dir.name}: '$name' ($shortText) is not shorter than '$replacedName' ($replacedText)")
                }
            }
        }
        if (failures.any()) {
            fail("Strings ending in '$SHORT_SUFFIX' replace a longer string when space is tight:\n" + failures.joinToString("\n"))
        }
    }

    private fun resourceDirs(): List<File> {
        // test working directory is the module directory: `AnkiDroid/`
        val resDir = File("../common/android/src/main/res")
        return resDir.listFiles { dir -> dir.name == "values" || dir.name.startsWith("values-") }?.toList()
            ?: error("Could not list files in $resDir")
    }

    private fun stringsIn(dir: File): Map<String, String> {
        val files = dir.listFiles { file -> file.extension == "xml" } ?: error("Could not list files in $dir")
        val result = mutableMapOf<String, String>()
        for (file in files) {
            val strings = documentBuilder.parse(file).getElementsByTagName("string")
            for (i in 0 until strings.length) {
                val element = strings.item(i)
                val name = element.attributes.getNamedItem("name")?.nodeValue ?: continue
                result[name] = element.textContent.trim()
            }
        }
        return result
    }

    companion object {
        private const val SHORT_SUFFIX = "_short"
    }
}

package com.musclenote.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Textes FR (par défaut) et EN (§ 9.4) : mêmes clés, mêmes arguments positionnels. */
class StringResourcesTest {

    @Test
    fun `chaque texte traduisible existe en français et en anglais`() {
        assertEquals(FR.keys.sorted(), EN.keys.sorted())
    }

    @Test
    fun `les arguments positionnels sont les mêmes dans les deux langues`() {
        for ((key, fr) in FR) assertEquals(key, fr, EN[key])
    }

    private companion object {
        // Déclarée avant FR et EN, qui s'en servent à l'initialisation.
        private val argument = Regex("""%(\d+)\$[sdf]""")

        val FR = arguments("src/main/res/values/strings.xml")
        val EN = arguments("src/main/res/values-en/strings.xml")

        /** Clé (`<string>` ou `<plurals>` traduisible) → arguments `%n$x` utilisés. */
        fun arguments(path: String): Map<String, Set<String>> {
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path)).documentElement
            val result = HashMap<String, Set<String>>()
            for (tag in listOf("string", "plurals")) {
                val nodes = root.getElementsByTagName(tag)
                for (i in 0 until nodes.length) {
                    val element = nodes.item(i) as Element
                    if (element.getAttribute("translatable") == "false") continue
                    result[element.getAttribute("name")] = argument.findAll(element.textContent).map { it.value }.toSet()
                }
            }
            return result
        }
    }
}

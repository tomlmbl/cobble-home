package io.github.tomlmbl.launcher.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** License texts joined back into paragraphs. */
class LicenseTextTest {

    @Test
    fun wrappedProseBecomesOneParagraphAndShortLinesStay() {
        val source = """
                                 Apache License
                           Version 2.0, January 2004

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.


   1. Definitions.
        """.trimIndent()
        assertEquals(
            "Apache License\nVersion 2.0, January 2004\n\n" +
                "\"License\" shall mean the terms and conditions for use, reproduction, and distribution as defined by Sections 1 through 9 of this document.\n\n" +
                "1. Definitions.",
            LicenseText.reflow(source),
        )
    }
}

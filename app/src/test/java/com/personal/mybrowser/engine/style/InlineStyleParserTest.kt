package com.personal.mybrowser.engine.style

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InlineStyleParserTest {
    @Test fun supportsShortAndLongHexColoursWithCssAlphaOrder() {
        for ((source, expected) in mapOf(
            "#abc" to 0xFFAABBCC.toInt(), "#abc8" to 0x88AABBCC.toInt(),
            "#A1b2C3" to 0xFFA1B2C3.toInt(), "#11223344" to 0x44112233,
            "#0000" to 0,
        )) {
            assertEquals(source, expected, InlineStyleParser.parse("color: $source").colorArgb)
        }
    }

    @Test fun supportsNonnegativeDecimalPixelSizesAndZero() {
        for ((source, expected) in mapOf("20px" to 20f, ".5px" to .5f, "+12.5PX" to 12.5f, "0px" to 0f, "0" to 0f)) {
            assertEquals(source, expected, InlineStyleParser.parse("font-size: $source").fontSizeCssPx)
        }
    }

    @Test fun namesAndUnitsIgnoreCaseAndWhitespaceAroundDeclarations() {
        assertEquals(InlineStyle(20f, 0xFF1565C0.toInt()), InlineStyleParser.parse("  COLOR : #1565c0 ; FONT-SIZE : 20PX ; "))
    }

    @Test fun lastValidDeclarationWinsIndependentlyForEachProperty() {
        assertEquals(
            InlineStyle(22f, 0xFF00FF00.toInt()),
            InlineStyleParser.parse("font-size: 18px; color: #f00; font-size: 22px; color: #0f0; font-size: wrong; color: nope"),
        )
    }

    @Test fun unsupportedAndMalformedDeclarationsAreIgnored() {
        assertEquals(InlineStyle(), InlineStyleParser.parse("font-weight: 900; margin: 20px; color; : #abc; font-size: ; color: "))
    }

    @Test fun invalidOrUnsupportedColoursAreNotAccepted() {
        for (value in listOf("red", "rgb(1,2,3)", "#12", "#12345", "#ggg", "#123456789", "#abc!important", "'#abc'")) {
            assertNull(value, InlineStyleParser.parse("color: $value").colorArgb)
        }
    }

    @Test fun invalidOrUnsupportedSizesAreNotAccepted() {
        for (value in listOf("-1px", "20", "2em", "120%", "NaNpx", "Infinitypx", "calc(2px + 3px)", "12 px", "10px!important", "9".repeat(100) + "px")) {
            assertNull(value, InlineStyleParser.parse("font-size: $value").fontSizeCssPx)
        }
    }

    @Test fun commentsDoNotBecomeValuesOrBreakFollowingDeclarations() {
        assertEquals(InlineStyle(20f, 0xFFAABBCC.toInt()), InlineStyleParser.parse("/* colour */ color: #abc; font-size/* note */: 20px; /* unfinished"))
        assertNull(InlineStyleParser.parse("font-size: 2/**/0px").fontSizeCssPx)
    }

    @Test fun semicolonsInUnsupportedQuotedOrFunctionalValuesDoNotCreateDeclarations() {
        assertEquals(InlineStyle(12f), InlineStyleParser.parse("unknown: 'x; color: #f00'; other: fn(x; color: #0f0); font-size: 12px"))
    }
}

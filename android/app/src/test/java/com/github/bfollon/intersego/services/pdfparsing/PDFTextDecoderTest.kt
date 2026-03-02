package com.github.bfollon.intersego.services.pdfparsing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PDFTextDecoderTest : FunSpec({

    context("needsDecoding") {
        test("clean text does not need decoding") {
            PDFTextDecoder.needsDecoding("LUNES A VIERNES 7:40 8:15") shouldBe false
        }

        test("text with many replacement chars needs decoding") {
            // >50% replacement characters triggers decoding
            val garbled = "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFDab"
            PDFTextDecoder.needsDecoding(garbled) shouldBe true
        }

        test("text with many control chars needs decoding") {
            // Build string with >50% control characters (excluding \n and \r)
            val controlChars = (1..10).map { it.toChar() }.joinToString("")
            val text = controlChars + "abc"
            PDFTextDecoder.needsDecoding(text) shouldBe true
        }

        test("empty text does not need decoding") {
            PDFTextDecoder.needsDecoding("") shouldBe false
        }

        test("text with newlines and carriage returns is fine") {
            PDFTextDecoder.needsDecoding("line1\nline2\rline3") shouldBe false
        }
    }

    context("decodeWithCharacterOffset") {
        test("offset 29 produces digits") {
            // '1' = 0x31, so raw code = 0x31 - 29 = 0x14 = 20 decimal
            val raw = buildString {
                append(20.toChar())  // -> '1'
                append(21.toChar())  // -> '2'
                append(22.toChar())  // -> '3'
            }
            PDFTextDecoder.decodeWithCharacterOffset(raw, 29) shouldBe "123"
        }

        test("offset 29 produces letters") {
            // 'A' = 0x41 = 65, raw = 65 - 29 = 36
            val rawA = 36.toChar()
            // 'a' = 0x61 = 97, raw = 97 - 29 = 68
            val rawSmallA = 68.toChar()
            PDFTextDecoder.decodeWithCharacterOffset("$rawA$rawSmallA", 29) shouldBe "Aa"
        }

        test("offset 29 produces punctuation") {
            // ':' = 0x3A = 58, raw = 58 - 29 = 29
            val rawColon = 29.toChar()
            val decoded = PDFTextDecoder.decodeWithCharacterOffset(rawColon.toString(), 29)
            decoded shouldBe ":"
        }

        test("null bytes are skipped") {
            val raw = buildString {
                append(0.toChar())   // null, skipped
                append(20.toChar())  // -> '1'
                append(0.toChar())   // null, skipped
            }
            PDFTextDecoder.decodeWithCharacterOffset(raw, 29) shouldBe "1"
        }

        test("out of range characters become question marks") {
            // Character that would decode to outside printable ASCII
            val raw = 200.toChar() // 200 + 29 = 229, outside 0x20..0x7E
            PDFTextDecoder.decodeWithCharacterOffset(raw.toString(), 29) shouldBe "?"
        }

        test("space is preserved") {
            // ' ' = 0x20 = 32, raw = 32 - 29 = 3
            val rawSpace = 3.toChar()
            PDFTextDecoder.decodeWithCharacterOffset(rawSpace.toString(), 29) shouldBe " "
        }

        test("decoding a realistic line with offset 29") {
            // "7:40" encoded: '7'=55-29=26, ':'=58-29=29, '4'=52-29=23, '0'=48-29=19
            val raw = buildString {
                append(26.toChar())  // -> '7'
                append(29.toChar())  // -> ':'
                append(23.toChar())  // -> '4'
                append(19.toChar())  // -> '0'
            }
            PDFTextDecoder.decodeWithCharacterOffset(raw, 29) shouldBe "7:40"
        }
    }
})

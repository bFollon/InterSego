/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.bfollon.linecapp.services.pdfparsing

import com.github.bfollon.linecapp.services.DebugConfig
import com.itextpdf.kernel.pdf.PdfPage
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.ITextExtractionStrategy
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo
import com.itextpdf.kernel.pdf.canvas.parser.EventType

/**
 * Utilities for decoding broken PDF font encodings
 *
 * Some PDFs from Linecar use broken font encodings where character codes
 * don't map correctly to Unicode. This utility provides decoders for
 * various encoding issues.
 */
object PDFTextDecoder {

    /**
     * Custom extraction strategy that captures raw glyph codes from PDF
     * instead of letting iText convert them to Unicode (which fails for broken PDFs)
     */
    class RawGlyphExtractionStrategy : ITextExtractionStrategy {
        private val result = StringBuilder()

        override fun getResultantText(): String = result.toString()

        override fun eventOccurred(data: IEventData, type: EventType) {
            if (type == EventType.RENDER_TEXT) {
                val renderInfo = data as TextRenderInfo

                // Get the raw PDF string bytes
                try {
                    val pdfString = renderInfo.pdfString

                    // Extract raw bytes from the PDF string
                    if (pdfString != null) {
                        // Get the text value (which contains the raw CID codes as character codes)
                        val text = pdfString.value

                        // Just append the text as-is - the character codes ARE the CID codes
                        result.append(text)
                    }
                } catch (e: Exception) {
                    DebugConfig.debugWarn("PDFTextDecoder: Failed to extract raw glyphs: ${e.message}")
                }
            }
        }

        override fun getSupportedEvents(): Set<EventType> {
            return setOf(EventType.RENDER_TEXT)
        }
    }

    /**
     * Decode text from PDF with character offset encoding
     *
     * Some PDFs use a fixed character offset for all text. For example,
     * the old M4 PDF uses +29 offset where all character codes need +29
     * added to get the correct character.
     *
     * @param text The raw text extracted from PDF
     * @param offset The character offset to add (default: 29)
     * @return Decoded text
     *
     * Examples with offset=29:
     * - 0x14 (DC4) + 29 = 0x31 = '1' ✓
     * - 0x15 (NAK) + 29 = 0x32 = '2' ✓
     * - 0x16 (SYN) + 29 = 0x33 = '3' ✓
     */
    fun decodeWithCharacterOffset(text: String, offset: Int = 29): String {
        val decoded = StringBuilder()

        for (char in text) {
            val code = char.code

            when (code) {
                // Skip null bytes (0x00) - these are part of 2-byte CID encoding
                0x00 -> continue

                // All other characters: add offset to get the actual character
                else -> {
                    val actualCode = code + offset
                    // Make sure it's in valid character range
                    if (actualCode in 0x20..0x7E || actualCode == 0x0A || actualCode == 0x0D) {
                        decoded.append(actualCode.toChar())
                    } else {
                        // For characters outside printable ASCII, use replacement char
                        decoded.append('?')
                    }
                }
            }
        }

        return decoded.toString()
    }

    /**
     * Extract text from a PDF page, automatically handling broken font encodings.
     *
     * Tries standard iText extraction first. If the text appears garbled
     * (high ratio of control/replacement characters), falls back to raw glyph
     * extraction with a +29 character offset (old Linecar PDF encoding).
     *
     * @param page The PDF page to extract text from
     * @param tag Log tag for debug output (e.g., "M4Parser")
     * @return Decoded text content of the page
     */
    fun extractText(page: PdfPage, tag: String = "PDFTextDecoder"): String {
        var text = PdfTextExtractor.getTextFromPage(page)
        if (!needsDecoding(text)) return text

        DebugConfig.debugPrint("$tag: Detected broken encoding, using custom decoder...")
        val strategy = RawGlyphExtractionStrategy()
        PdfCanvasProcessor(strategy).processPageContent(page)
        text = decodeWithCharacterOffset(strategy.resultantText, offset = 29)
        DebugConfig.debugPrint("$tag: Successfully decoded broken PDF")
        return text
    }

    /**
     * Detect if text needs decoding by checking for high ratio of replacement characters
     *
     * @param text Text to check
     * @return true if text appears to need decoding (many replacement chars or control codes)
     */
    fun needsDecoding(text: String): Boolean {
        if (text.isEmpty()) return false

        val replacementChars = text.count { it == '\uFFFD' }
        val controlChars = text.count { it.code < 0x20 && it.code != 0x0A && it.code != 0x0D }
        val totalChars = text.length

        // If more than 50% of characters are replacement chars or control codes, needs decoding
        val problematicRatio = (replacementChars + controlChars).toFloat() / totalChars
        return problematicRatio > 0.5f
    }

}

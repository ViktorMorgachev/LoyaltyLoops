package io.loyaltyloop.app.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class MaskVisualTransformation(private val mask: String) : VisualTransformation {

    private val specialChars = setOf(' ', '-', '(', ')')

    override fun filter(text: AnnotatedString): TransformedText {
        var out = ""
        var maskIndex = 0
        text.text.forEach { char ->
            while (specialChars.contains(mask.getOrNull(maskIndex))) {
                out += mask[maskIndex]
                maskIndex++
            }
            out += char
            maskIndex++
        }
        return TransformedText(AnnotatedString(out), offsetMapping)
    }

    private val offsetMapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            var maskedOffset = 0
            var rawOffset = 0
            while (rawOffset < offset && maskedOffset < mask.length) {
                if (specialChars.contains(mask[maskedOffset])) {
                    maskedOffset++
                } else {
                    rawOffset++
                    maskedOffset++
                }
            }
            return maskedOffset
        }

        override fun transformedToOriginal(offset: Int): Int {
            var maskedOffset = 0
            var rawOffset = 0
            while (maskedOffset < offset && maskedOffset < mask.length) {
                if (!specialChars.contains(mask[maskedOffset])) {
                    rawOffset++
                }
                maskedOffset++
            }
            return rawOffset
        }
    }
}
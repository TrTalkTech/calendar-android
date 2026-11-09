package com.example.calendar.data

import android.graphics.Typeface
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

object TaskNotesHtml {
    fun toAnnotated(raw: String): AnnotatedString {
        if (raw.isBlank()) return AnnotatedString("")
        if (!raw.contains('<')) return AnnotatedString(raw)
        val spanned = Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT)
        return spanned.toAnnotatedString()
    }

    fun toHtml(text: AnnotatedString): String {
        if (text.text.isBlank()) return ""
        if (text.spanStyles.isEmpty()) return text.text
        val spannable = SpannableString(text.text)
        text.spanStyles.forEach { range ->
            val start = range.start.coerceIn(0, spannable.length)
            val end = range.end.coerceIn(start, spannable.length)
            if (start >= end) return@forEach
            val style = range.item
            val typeface = when {
                style.fontWeight == FontWeight.Bold && style.fontStyle == FontStyle.Italic ->
                    Typeface.BOLD_ITALIC
                style.fontWeight == FontWeight.Bold -> Typeface.BOLD
                style.fontStyle == FontStyle.Italic -> Typeface.ITALIC
                else -> null
            }
            if (typeface != null) {
                spannable.setSpan(StyleSpan(typeface), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (style.textDecoration?.contains(TextDecoration.Underline) == true) {
                spannable.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return Html.toHtml(spannable, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL).trim()
    }
}

private fun Spanned.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    append(this@toAnnotatedString.toString())
    getSpans(0, length, Any::class.java).forEach { span ->
        val start = getSpanStart(span).coerceAtLeast(0)
        val end = getSpanEnd(span).coerceAtMost(length)
        if (start >= end) return@forEach
        when (span) {
            is StyleSpan -> when (span.style) {
                Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                Typeface.BOLD_ITALIC -> addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                    start,
                    end,
                )
            }
            is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
        }
    }
}

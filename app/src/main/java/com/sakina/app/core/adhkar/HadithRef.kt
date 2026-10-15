package com.sakina.app.core.adhkar

import kotlinx.serialization.Serializable

/** A short quotation from a verified narration, shown as a reference on the calendar and seasons pages. */
@Serializable
data class HadithRef(
    val text: String,
    val book: String,
    val number: String,
    val ar: String = "",
    val en: String = "",
    val grade: Grade,
    val graders: List<Grader> = emptyList(),
    val url: String,
) {
    /** True when one of the graders calls it weak (shown with all graders, never hidden). */
    val disputed: Boolean get() = graders.any { it.grade.contains("daif", ignoreCase = true) }
}

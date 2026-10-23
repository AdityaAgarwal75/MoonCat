package com.nuvio.tv.core.catalog

/** Home shelf visibility only. Never filters titles, source files or audio tracks. */
object MoonCatHomeCatalogPolicy {
    private val regionalIds = setOf("ta", "te", "kn", "ml", "bn", "mr", "pa", "gu",
        "tamil", "telugu", "kannada", "malayalam", "bengali", "marathi", "punjabi", "gujarati")
    private val regionalName = Regex("(?i)^(tamil|telugu|kannada|malayalam|bengali|marathi|punjabi|gujarati|regional)\\b")
    fun hidden(id: String, name: String): Boolean = id.lowercase() in regionalIds || regionalName.containsMatchIn(name.trim())
}

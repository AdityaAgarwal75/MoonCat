package com.nuvio.tv.core.catalog

import com.google.gson.JsonObject

/** TVmaze public metadata, accessed 2026-10-20. See mooncat/DISCOVERY-SOURCES.md for attribution.
 * Discovery entries do not promise that an installed provider carries every episode.
 */
internal fun hindiTvSerials(): List<JsonObject> = listOf(
        JsonObject().apply {
            addProperty("id", "tt11867666")
            addProperty("type", "series")
            addProperty("name", "Anupama")
            addProperty("poster", "https://static.tvmaze.com/uploads/images/original_untouched/519/1298119.jpg")
            addProperty("releaseInfo", "2020")
        },
        JsonObject().apply {
            addProperty("id", "tt1708446")
            addProperty("type", "series")
            addProperty("name", "Taarak Mehta Ka Ooltah Chashmah")
            addProperty("poster", "https://static.tvmaze.com/uploads/images/original_untouched/518/1295584.jpg")
            addProperty("releaseInfo", "2008")
        },
        JsonObject().apply {
            addProperty("id", "tt3911870")
            addProperty("type", "series")
            addProperty("name", "Kumkum Bhagya")
            addProperty("poster", "https://static.tvmaze.com/uploads/images/original_untouched/501/1252962.jpg")
            addProperty("releaseInfo", "2014")
        },
        JsonObject().apply {
            addProperty("id", "tt13085534")
            addProperty("type", "series")
            addProperty("name", "Ghum Hai Kisi Ke Pyaar Mein")
            addProperty("poster", "https://static.tvmaze.com/uploads/images/original_untouched/295/738102.jpg")
            addProperty("releaseInfo", "2020")
        },
        JsonObject().apply {
            addProperty("id", "tt13853152")
            addProperty("type", "series")
            addProperty("name", "Wagle Ki Duniya")
            addProperty("poster", "https://static.tvmaze.com/uploads/images/original_untouched/518/1295592.jpg")
            addProperty("releaseInfo", "2021")
        },
        JsonObject().apply {
            addProperty("id", "tt5154982")
            addProperty("type", "series")
            addProperty("name", "Bhabi Ji Ghar Par Hai!")
            addProperty("poster", "https://static.tvmaze.com/uploads/images/original_untouched/518/1296499.jpg")
            addProperty("releaseInfo", "2015")
        }
)

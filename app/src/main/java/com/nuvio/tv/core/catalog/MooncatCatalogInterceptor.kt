package com.nuvio.tv.core.catalog

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.nuvio.tv.domain.model.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.URLDecoder
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** App-owned discovery descriptor. Public providers are called over verified HTTPS. */
class MooncatCatalogInterceptor(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()
) : Interceptor {
    private val gson = Gson()
    private data class Cached(val at: Long, val json: String)
    private val cache = ConcurrentHashMap<String, Cached>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != HOST) return chain.proceed(request)
        val parts = request.url.pathSegments
        val language = parts.firstOrNull()?.takeIf { it == "hi" } ?: "en"
        return try {
            val json = if (parts.lastOrNull() == "manifest.json") gson.toJson(manifest("https://$HOST/$language"))
            else if (parts.getOrNull(1) == "catalog") {
                val type = parts.getOrNull(2) ?: throw IOException("Missing type")
                val id = parts.getOrNull(3)?.removeSuffix(".json") ?: throw IOException("Missing catalog")
                val extra = parts.getOrNull(4)?.removeSuffix(".json").orEmpty().split('&').mapNotNull {
                    val pair = it.split('=', limit = 2)
                    if (pair.size == 2) pair[0] to URLDecoder.decode(pair[1], "UTF-8") else null
                }.toMap()
                catalog(language, type, id, extra["skip"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0)
            } else throw IOException("Unsupported resource")
            response(request, 200, json)
        } catch (error: Exception) {
            response(request, 502, gson.toJson(mapOf("error" to "Discovery provider unavailable. Try again later.")))
        }
    }

    private fun catalog(language: String, type: String, id: String, skip: Int): String {
        if (id == "airing") return airing(language, skip)
        if (id == "hindi-serials" && type == "series") return gson.toJson(mapOf("metas" to hindiTvSerials().drop(skip).take(20)))
        if (id == "anime-upcoming") return upcomingAnime(skip)
        val regional = REGIONAL_SHELVES[id]
        if (regional != null && regional.first == type) {
            return getJson("$INDIAN_CATALOG/catalog/$type/${regional.second}/skip=$skip.json")
        }
        val genre = GENRE_SHELVES[id]
        if (genre != null) {
            val encoded = java.net.URLEncoder.encode(genre, "UTF-8")
            return getJson("https://v3-cinemeta.strem.io/catalog/$type/top/genre=$encoded&skip=$skip.json")
        }
        val config = if (language == "hi") HINDI_CONFIG else ENGLISH_CONFIG
        val provider = "$TMDB/$config/catalog/$type"
        val url = when (id) {
            // Upstream tmdb.language sets with_original_language, not just translated metadata.
            "language" -> "$provider/tmdb.language/skip=$skip.json"
            "upcoming" -> "$provider/tmdb.year/genre=${LocalDate.now().year + 1}&skip=$skip.json"
            "new" -> "$provider/tmdb.latest/skip=$skip.json"
            else -> throw IOException("Unknown catalog")
        }
        // Prefer a Hindi-only source with IMDb IDs and artwork. The older TMDB
        // endpoint frequently times out, which used to leave the Hindi home empty.
        if (language == "hi" && id == "language" && type in setOf("movie", "series")) {
            val regionalId = if (type == "series") "hi_series" else "hi"
            val regional = "$INDIAN_CATALOG/catalog/$type/$regionalId/skip=$skip.json"
            return try {
                val payload = getJson(regional)
                val metas = gson.fromJson(payload, JsonObject::class.java)?.getAsJsonArray("metas")
                if (metas == null || metas.size() == 0) throw IOException("Hindi catalogue empty")
                payload
            } catch (_: Exception) {
                getJson(url) // Independent backup provider, never fake Hindi titles.
            }
        }
        return getJson(url)
    }

    private fun upcomingAnime(skip: Int): String {
        val json = gson.fromJson(getJson("https://kitsu.io/api/edge/anime?filter%5Bstatus%5D=upcoming&page%5Boffset%5D=$skip&page%5Blimit%5D=20&sort=-user_count"), JsonObject::class.java)
        val metas = json.getAsJsonArray("data").map { entry ->
            val item = entry.asJsonObject
            val attrs = item.getAsJsonObject("attributes")
            JsonObject().apply {
                addProperty("id", "kitsu:${item.get("id").asString}")
                addProperty("type", if (attrs.get("subtype")?.asString == "movie") "movie" else "series")
                addProperty("name", attrs.get("canonicalTitle").asString)
                attrs.get("synopsis")?.takeUnless { it.isJsonNull }?.let { add("description", it) }
                attrs.get("startDate")?.takeUnless { it.isJsonNull }?.let { add("released", it); addProperty("releaseInfo", it.asString.take(4)) }
                attrs.get("posterImage")?.takeUnless { it.isJsonNull }?.asJsonObject?.get("large")?.let { add("poster", it) }
                attrs.get("coverImage")?.takeUnless { it.isJsonNull }?.asJsonObject?.get("large")?.let { add("background", it) }
            }
        }
        return gson.toJson(mapOf("metas" to metas))
    }

    private fun airing(language: String, skip: Int): String {
        val country = if (language == "hi") "IN" else "US"
        val data = gson.fromJson(getJson("https://api.tvmaze.com/schedule?country=$country&date=${LocalDate.now()}"), JsonArray::class.java)
        val shows = data.mapNotNull { it.asJsonObject.getAsJsonObject("show") }
            .filter { it.get("language")?.takeUnless { value -> value.isJsonNull }?.asString == if (language == "hi") "Hindi" else "English" }
            .distinctBy { it.get("id").asInt }.mapNotNull { show ->
                val imdb = show.getAsJsonObject("externals")?.get("imdb")?.takeUnless { it.isJsonNull }?.asString ?: return@mapNotNull null
                JsonObject().apply {
                    addProperty("id", imdb); addProperty("type", "series"); addProperty("name", show.get("name").asString)
                    show.get("image")?.takeUnless { it.isJsonNull }?.asJsonObject?.get("original")?.let { add("poster", it) }
                    show.get("summary")?.takeUnless { it.isJsonNull }?.asString?.let { addProperty("description", it.replace(Regex("<[^>]*>"), "")) }
                    show.get("premiered")?.takeUnless { it.isJsonNull }?.asString?.let { addProperty("releaseInfo", it.take(4)) }
                }
            }
        return gson.toJson(mapOf("metas" to shows.drop(skip).take(20)))
    }

    private fun getJson(url: String): String {
        cache[url]?.takeIf { System.currentTimeMillis() - it.at < TimeUnit.MINUTES.toMillis(15) }?.let { return it.json }
        client.newCall(Request.Builder().url(url).header("User-Agent", "MoonCat/1.0").build()).execute().use {
            if (!it.isSuccessful) throw IOException("Catalog HTTP ${it.code}")
            val json = it.body?.string() ?: throw IOException("Empty catalog")
            if (cache.size > 96) cache.clear()
            cache[url] = Cached(System.currentTimeMillis(), json)
            return json
        }
    }

    private fun response(request: Request, code: Int, json: String) = Response.Builder().request(request)
        .protocol(Protocol.HTTP_1_1).code(code).message(if (code == 200) "OK" else "Provider unavailable")
        .header("Cache-Control", "no-store").body(json.toResponseBody("application/json".toMediaType())).build()

    companion object {
        const val HOST = "catalog.mooncat.local"
        const val INDIAN_CATALOG = "https://indian-regional-catalog.vercel.app"
        const val TMDB = "https://94c8cb9f702d-tmdb-addon.baby-beamup.club"
        private val REGIONAL_SHELVES = mapOf(
            "hindi-dubbed" to ("movie" to "hindi_dubbed"),
            "hindi-tv" to ("series" to "hi_series"),
            "ott" to ("movie" to "ott_releases"),
            "tamil" to ("movie" to "ta"), "telugu" to ("movie" to "te"),
            "malayalam" to ("movie" to "ml"), "punjabi" to ("movie" to "pa")
        )
        private val GENRE_SHELVES = mapOf("family" to "Family", "comedy" to "Comedy", "documentary" to "Documentary", "crime" to "Crime")
        // lz-string encoded {language, returnImdbId:true, provideImdbId:true}; no API credentials.
        private const val HINDI_CONFIG = "N4IgNghgdg5grhGBTEAuEALAlgWgJIByIANCAE5IAucZUeAtgCYBGejaIlZcKpADmQD2ANyyMkDFmw5ceIAL5A"
        private const val ENGLISH_CONFIG = "N4IgNghgdg5grhGBTEAuESoFoCqBlEAGhACckAXOEqASQFsATAIxobRHJLhWIAcSA9gDcAlgyT1mrdp24gAvkA"

        /** Bootstrap documented resource IDs so first launch does not wait on remote manifests. */
        fun bootstrapManifest(baseUrl: String): Addon? {
            manifest(baseUrl)?.let { return it }
            val httpProvider = when (baseUrl.trimEnd('/')) {
                "https://torrentsdb.com" -> "TorrentsDB"
                "https://hdhub.thevolecitor.qzz.io" -> "HDHub"
                else -> null
            }
            if (httpProvider != null) {
                return Addon(id = "mooncat.provider.${if (httpProvider == "HDHub") "hdhub" else "torrentsdb"}",
                    name = httpProvider, version = "1.0.0", description = "Free source discovery with no subscription. Public instance availability varies; configure or replace its URL in Addons if needed.",
                    logo = null, baseUrl = baseUrl, catalogs = emptyList(), types = listOf(ContentType.MOVIE, ContentType.SERIES),
                    idPrefixes = listOf("tt"), resources = listOf(AddonResource("stream", listOf("movie", "series"), listOf("tt"))))
            }
            if (baseUrl == "https://v3-cinemeta.strem.io") {
                val genres = listOf("Action", "Adventure", "Animation", "Biography", "Comedy", "Crime", "Documentary", "Drama", "Family", "Fantasy", "History", "Horror", "Mystery", "Romance", "Sci-Fi", "Sport", "Thriller", "War", "Western")
                val catalogs = listOf(ContentType.MOVIE, ContentType.SERIES).flatMap { type ->
                    listOf(CatalogDescriptor(type = type, id = "top", name = "Popular", pageSize = 100,
                        extra = listOf(CatalogExtra("genre", options = genres), CatalogExtra("search"), CatalogExtra("skip")), extraSupported = listOf("genre", "search", "skip")),
                        CatalogDescriptor(type = type, id = "year", name = "New", pageSize = 100,
                            extra = listOf(CatalogExtra("genre", isRequired = true, options = (LocalDate.now().year downTo 1920).map { it.toString() }), CatalogExtra("skip")),
                            extraSupported = listOf("genre", "skip"), extraRequired = listOf("genre")),
                        CatalogDescriptor(type = type, id = "imdbRating", name = "Featured", pageSize = 100,
                            extra = listOf(CatalogExtra("genre", options = genres), CatalogExtra("skip")), extraSupported = listOf("genre", "skip")))
                }
                return Addon(id = "com.linvo.cinemeta", name = "Cinemeta", version = "3.0.0", description = "Movie and series discovery by Cinemeta.",
                    logo = null, baseUrl = baseUrl, catalogs = catalogs, types = listOf(ContentType.MOVIE, ContentType.SERIES),
                    idPrefixes = listOf("tt"), resources = listOf(AddonResource("catalog", listOf("movie", "series"), null), AddonResource("meta", listOf("movie", "series"), listOf("tt"))))
            }
            if (baseUrl == "https://anime-kitsu.strem.fun") {
                val catalogs = listOf("kitsu-anime-trending" to "Anime · Trending", "kitsu-anime-airing" to "Anime · Airing",
                    "kitsu-anime-popular" to "Anime · Popular", "kitsu-anime-rating" to "Anime · Top rated").map { (id, title) ->
                    val skip = if (id == "kitsu-anime-trending") emptyList() else listOf(CatalogExtra("skip"))
                    CatalogDescriptor(type = ContentType.UNKNOWN, rawType = "anime", id = id, name = title, pageSize = if (id in setOf("hindi-dubbed", "ott")) 10 else 20,
                        showInHome = true, hasExplicitShowInHome = true, extra = skip, extraSupported = skip.map { it.name })
                } + CatalogDescriptor(type = ContentType.UNKNOWN, rawType = "anime", id = "kitsu-anime-list", name = "Anime · Search",
                    extra = listOf(CatalogExtra("search", isRequired = true), CatalogExtra("skip")), extraSupported = listOf("search", "skip"), extraRequired = listOf("search"))
                return Addon(id = "community.anime.kitsu", name = "Anime Kitsu", version = "0.0.10",
                    description = "Anime catalogs and metadata by the community Kitsu addon.", logo = null, baseUrl = baseUrl,
                    catalogs = catalogs, types = listOf(ContentType.MOVIE, ContentType.SERIES), rawTypes = listOf("anime", "movie", "series"),
                    idPrefixes = listOf("kitsu", "mal", "anilist", "anidb"), resources = listOf(
                        AddonResource("catalog", listOf("anime"), null),
                        AddonResource("meta", listOf("anime", "movie", "series"), listOf("kitsu", "mal", "anilist", "anidb"))))
            }
            if (baseUrl == "https://torrentio.strem.fun/language=hindi" || baseUrl == "https://torrentio.strem.fun/language=english") {
                return Addon(id = "com.stremio.torrentio", name = "Torrentio", version = "0.0.15", description = "User-configured Torrentio stream provider.",
                    logo = null, baseUrl = baseUrl, catalogs = emptyList(), types = listOf(ContentType.MOVIE, ContentType.SERIES), rawTypes = listOf("movie", "series", "anime"),
                    idPrefixes = listOf("tt", "kitsu"), resources = listOf(AddonResource("stream", listOf("movie", "series", "anime"), listOf("tt", "kitsu"))))
            }
            return null
        }

        fun manifest(baseUrl: String): Addon? {
            if (!baseUrl.startsWith("https://$HOST/")) return null
            val language = if (baseUrl.endsWith("/hi")) "Hindi" else "English"
            fun shelf(type: ContentType, id: String, title: String) = CatalogDescriptor(type = type, id = id, name = title, pageSize = if (id in setOf("hindi-dubbed", "ott")) 10 else 20,
                extra = listOf(CatalogExtra("skip")), extraSupported = listOf("skip"), showInHome = true, hasExplicitShowInHome = true)
            return Addon(id = "mooncat.discovery.${if (language == "Hindi") "hi" else "en"}", name = "MoonCat Discovery", version = "1.0.0",
                description = "Language discovery and new releases: TMDB Addon / The Movie Database. Airing today: TVmaze. Anime: Kitsu. Upcoming movies cover next calendar year. This product uses the TMDB API but is not endorsed or certified by TMDB.",
                logo = null, baseUrl = baseUrl, types = listOf(ContentType.MOVIE, ContentType.SERIES), catalogs = listOf(
                    shelf(ContentType.MOVIE, "language", "$language movies"), shelf(ContentType.SERIES, "language", "$language series · TV & web shows"),
                    shelf(ContentType.SERIES, "hindi-serials", "Hindi TV serials"),
                    shelf(ContentType.MOVIE, "hindi-dubbed", "Hindi dubbed movies"),
                    shelf(ContentType.MOVIE, "ott", "Indian OTT releases"),
                    shelf(ContentType.MOVIE, "family", "Family movies"),
                    shelf(ContentType.SERIES, "comedy", "Comedy series"),
                    shelf(ContentType.SERIES, "crime", "Crime & thrillers"),
                    shelf(ContentType.MOVIE, "documentary", "Documentaries"),
                    shelf(ContentType.MOVIE, "tamil", "Tamil movies"),
                    shelf(ContentType.MOVIE, "telugu", "Telugu movies"),
                    shelf(ContentType.MOVIE, "malayalam", "Malayalam movies"),
                    shelf(ContentType.MOVIE, "punjabi", "Punjabi movies"),
                    shelf(ContentType.MOVIE, "new", "New movie releases · worldwide"), shelf(ContentType.SERIES, "new", "New series · worldwide"),
                    shelf(ContentType.MOVIE, "upcoming", "Upcoming movies · ${LocalDate.now().year + 1}"),
                    shelf(ContentType.SERIES, "airing", "$language · Airing today"), shelf(ContentType.SERIES, "anime-upcoming", "Anime · Upcoming")
                ), resources = listOf(AddonResource("catalog", listOf("movie", "series"), null)))
        }
    }
}

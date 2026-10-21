package com.nuvio.tv.core.catalog

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class MooncatCatalogInterceptorTest {
    private fun client(body: String, capture: (String) -> Unit = {}): OkHttpClient {
        val provider = OkHttpClient.Builder().addInterceptor { chain ->
            capture(chain.request().url.toString())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return OkHttpClient.Builder().addInterceptor(MooncatCatalogInterceptor(provider)).build()
    }

    @Test fun languageShelfUsesOriginalLanguageCatalogAndRetainsPagination() {
        var providerUrl = ""
        val client = client("{\"metas\":[]}") { providerUrl = it }
        client.newCall(Request.Builder().url("https://catalog.mooncat.local/hi/catalog/movie/language/skip=60.json").build()).execute().use {
            assertEquals(200, it.code)
            assertTrue(providerUrl.contains("/tmdb.language/skip=60.json"))
            assertTrue(providerUrl.contains("N4IgNghgdg5grhGBTEAuEALAlg"))
        }
    }

    @Test fun airingFiltersLanguageDeduplicatesAndKeepsOnlyResolvableImdbIds() {
        val body = """[
          {"show":{"id":1,"name":"Hindi Show","language":"Hindi","externals":{"imdb":"tt123"},"image":null}},
          {"show":{"id":1,"name":"Hindi Show","language":"Hindi","externals":{"imdb":"tt123"},"image":null}},
          {"show":{"id":2,"name":"English Show","language":"English","externals":{"imdb":"tt456"},"image":null}},
          {"show":{"id":3,"name":"Unresolvable","language":"Hindi","externals":{"imdb":null},"image":null}}
        ]"""
        var providerUrl = ""
        client(body) { providerUrl = it }.newCall(Request.Builder()
            .url("https://catalog.mooncat.local/hi/catalog/series/airing.json").build()).execute().use {
            val metas = JsonParser.parseString(it.body!!.string()).asJsonObject.getAsJsonArray("metas")
            assertEquals(1, metas.size())
            assertEquals("tt123", metas[0].asJsonObject.get("id").asString)
            assertTrue(providerUrl.contains("country=IN"))
        }
    }

    @Test fun malformedProviderResponseReturnsRecoverableError() {
        client("not json").newCall(Request.Builder()
            .url("https://catalog.mooncat.local/en/catalog/series/airing.json").build()).execute().use {
            assertEquals(502, it.code)
        }
    }

    @Test fun manifestExposesHonestShelfNamesAndSkipSupport() {
        val manifest = MooncatCatalogInterceptor.manifest("https://catalog.mooncat.local/hi")!!
        assertTrue(manifest.catalogs.any { it.name == "Hindi movies" })
        assertTrue(manifest.catalogs.any { it.name == "New movie releases · worldwide" })
        assertTrue(manifest.catalogs.all { catalog -> catalog.extra.any { it.name == "skip" } })
        assertNull(MooncatCatalogInterceptor.manifest("https://unrelated.example/hi"))
    }
    @Test fun bootstrapProvidesAnimeAndStreamsWithoutWaitingForRemoteManifests() {
        val anime = MooncatCatalogInterceptor.bootstrapManifest("https://anime-kitsu.strem.fun")!!
        assertTrue(anime.catalogs.any { it.id == "kitsu-anime-airing" })
        assertTrue(anime.resources.any { it.name == "meta" && it.idPrefixes.orEmpty().contains("kitsu") })
        val streams = MooncatCatalogInterceptor.bootstrapManifest("https://torrentio.strem.fun/language=hindi")!!
        assertTrue(streams.resources.any { it.name == "stream" })
        assertTrue(streams.catalogs.isEmpty())
    }

    @Test fun hindiDubbedShelfUsesRegionalProviderWithPagination() {
        var providerUrl = ""
        client("{\"metas\":[]}") { providerUrl = it }.newCall(Request.Builder()
            .url("https://catalog.mooncat.local/hi/catalog/movie/hindi-dubbed/skip=20.json").build()).execute().use {
            assertEquals(200, it.code)
            assertTrue(providerUrl.endsWith("/catalog/movie/hindi_dubbed/skip=20.json"))
        }
    }

    @Test fun newSourcesDoNotDependOnManifestFetchBeforeEpisodeSearch() {
        val comet = MooncatCatalogInterceptor.bootstrapManifest("https://comet.elfhosted.com")!!
        assertTrue(comet.resources.any { it.name == "stream" && "series" in it.types })
        assertTrue(comet.catalogs.isEmpty())
        val youtube = MooncatCatalogInterceptor.bootstrapManifest("https://v3-channels.strem.io")!!
        assertTrue(youtube.catalogs.any { it.type == com.nuvio.tv.domain.model.ContentType.CHANNEL })
        assertFalse(youtube.resources.any { it.name == "stream" })
        assertTrue(youtube.resources.any { it.name == "meta" })
    }

    @Test fun freeProvidersBootstrapAsStreamOnlyWithoutDuplicatingHomeCatalogues() {
        listOf("https://torrentsdb.com", "https://hdhub.thevolecitor.qzz.io").forEach { url ->
            val addon = MooncatCatalogInterceptor.bootstrapManifest(url)!!
            assertTrue(addon.resources.any { it.name == "stream" && "tt" in it.idPrefixes.orEmpty() })
            assertTrue(addon.catalogs.isEmpty())
        }
    }

}

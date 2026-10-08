package fr.carnetfaune.app.data

import retrofit2.http.GET
import retrofit2.http.Query

private const val TAXREF = "0e61f8fe-7d25-4f81-ada7-d970bbb2c6d6"

data class SearchResponse(val endOfRecords: Boolean, val results: List<TaxonResult>)
data class TaxonResult(val key: Long, val scientificName: String?, val canonicalName: String?, val rank: String?, val taxonomicStatus: String?, val className: String?, val order: String?, val vernacularName: String?)
data class MediaResponse(val results: List<MediaItem>)
data class MediaItem(val identifier: String?, val type: String?, val format: String?, val license: String?, val creator: String?, val title: String?)
data class OccurrenceResponse(val results: List<OccurrenceItem>)
data class OccurrenceItem(val media: List<MediaItem>?)
data class TaxrefResponse(
    val id: Long? = null,
    val scientificName: String? = null,
    val vernacularNames: List<TaxrefVernacularName>? = null
)

data class TaxrefVernacularName(
    val name: String? = null,
    val language: String? = null,
    val locality: String? = null
)
interface GbifApi {
    @GET("species/search") suspend fun searchSpecies(
        @Query("datasetKey") datasetKey: String = TAXREF,
        @Query("rank") rank: String = "SPECIES",
        @Query("higherTaxonKey") higherTaxonKey: Long,
        @Query("status") status: String = "ACCEPTED",
        @Query("limit") limit: Int = 300,
        @Query("offset") offset: Int = 0
    ): SearchResponse
@GET("https://taxref.mnhn.fr/api/taxa/{id}")
suspend fun taxrefTaxon(
    @retrofit2.http.Path("id") id: Long
): TaxrefResponse
    @GET("species/search") suspend fun findHigherTaxon(
        @Query("datasetKey") datasetKey: String = TAXREF,
        @Query("rank") rank: String,
        @Query("q") q: String,
        @Query("limit") limit: Int = 20
    ): SearchResponse

    @GET("species/{key}/media") suspend fun media(@retrofit2.http.Path("key") key: Long, @Query("limit") limit: Int = 1): MediaResponse

    @GET("occurrence/search") suspend fun occurrenceWithImage(@Query("taxon_key") key: Long, @Query("media_type") mediaType: String = "StillImage", @Query("country") country: String = "FR", @Query("limit") limit: Int = 1): OccurrenceResponse
}
interface TaxrefApi {

    @GET("api/taxa/{id}")
    suspend fun getTaxon(
        @retrofit2.http.Path("id") id: Long
    ): TaxrefResponse
}

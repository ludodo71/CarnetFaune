package fr.carnetfaune.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

private const val TAXREF_DATASET =
    "0e61f8fe-7d25-4f81-ada7-d970bbb2c6d6"

// ------------------------------------------------------------
// GBIF
// ------------------------------------------------------------

data class SearchResponse(
    val endOfRecords: Boolean,
    val results: List<TaxonResult>
)

data class VernacularNamesResponse(
    val results: List<GbifVernacularName>?
)

data class GbifVernacularName(
    val vernacularName: String?,
    val language: String?,
    val preferred: Boolean?
)
data class TaxonResult(
    val key: Long,
    val scientificName: String?,
    val canonicalName: String?,
    val rank: String?,
    val taxonomicStatus: String?,
    val className: String?,
    val order: String?,
    val vernacularName: String?
)

data class MediaResponse(
    val results: List<MediaItem>
)

data class MediaItem(
    val identifier: String?,
    val type: String?,
    val format: String?,
    val license: String?,
    val creator: String?,
    val title: String?
)

data class OccurrenceResponse(
    val results: List<OccurrenceItem>
)

data class OccurrenceItem(
    val media: List<MediaItem>?
)

interface GbifApi {

    @GET("species/search")
    suspend fun searchSpecies(
        @Query("datasetKey") datasetKey: String = TAXREF_DATASET,
        @Query("rank") rank: String = "SPECIES",
        @Query("higherTaxonKey") higherTaxonKey: Long,
        @Query("status") status: String = "ACCEPTED",
        @Query("limit") limit: Int = 300,
        @Query("offset") offset: Int = 0
    ): SearchResponse

    @GET("species/search")
    suspend fun findHigherTaxon(
        @Query("datasetKey") datasetKey: String = TAXREF_DATASET,
        @Query("rank") rank: String,
        @Query("q") q: String,
        @Query("limit") limit: Int = 20
    ): SearchResponse

    @GET("species/{key}/media")
    suspend fun media(
        @Path("key") key: Long,
        @Query("limit") limit: Int = 1
    ): MediaResponse

@GET("species/{key}/vernacularNames")
suspend fun vernacularNames(
    @Path("key") key: Long
): VernacularNamesResponse
    @GET("occurrence/search")
    suspend fun occurrenceWithImage(
        @Query("taxon_key") key: Long,
        @Query("media_type") mediaType: String = "StillImage",
        @Query("country") country: String = "FR",
        @Query("limit") limit: Int = 1
    ): OccurrenceResponse
}

// ------------------------------------------------------------
// TAXREF
// ------------------------------------------------------------


data class TaxrefSearchResponse(
    @SerializedName("_embedded")
    val embedded: TaxrefEmbedded?
)

data class TaxrefEmbedded(
    val taxa: List<TaxrefTaxon>?
)

data class TaxrefTaxon(
    val id: Long?,
    val scientificName: String?,
    val vernacularName: String?,
    val vernacularNames: List<TaxrefVernacularName>?
)

data class TaxrefVernacularName(
    val name: String?,
    val language: String?
)

interface TaxrefApi {

    @GET("api/taxa/fuzzyMatch")
    suspend fun fuzzyMatch(
        @Query("term") term: String
    ): TaxrefSearchResponse

    @GET("api/taxa/{id}")
    suspend fun getTaxon(
        @Path("id") id: Long
    ): TaxrefTaxon
}

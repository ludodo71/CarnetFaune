package fr.carnetfaune.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppRepository(private val db: AppDatabase) {
    val species: Flow<List<Species>> = db.speciesDao().observeAll()
    val places: Flow<List<Place>> = db.placeDao().observeAll()
    val observations: Flow<List<Observation>> = db.observationDao().observeAll()
    private val api = Retrofit.Builder().baseUrl("https://api.gbif.org/v1/").addConverterFactory(GsonConverterFactory.create()).build().create(GbifApi::class.java)

    suspend fun addPlace(name: String, lat: Double?, lon: Double?, habitat: String) = db.placeDao().insert(Place(name = name.trim(), latitude = lat, longitude = lon, habitat = habitat.trim()))
    suspend fun deletePlace(p: Place) = db.placeDao().delete(p)
    suspend fun addObservation(o: Observation) = db.observationDao().insert(o)
    suspend fun deleteObservation(o: Observation) = db.observationDao().delete(o)
    suspend fun snapshot() = db.observationDao().snapshot()
    suspend fun firstImageUrl(speciesId: Long): String? = withContext(Dispatchers.IO) {
        runCatching { api.media(speciesId).results.firstOrNull()?.identifier }
            .recoverCatching { api.occurrenceWithImage(speciesId).results.firstOrNull()?.media?.firstOrNull()?.identifier }
            .getOrNull()
    }

    suspend fun syncCatalog(onProgress: (String) -> Unit) = withContext(Dispatchers.IO) {
        val groups = listOf("Aves" to "OISEAUX", "Mammalia" to "MAMMIFÈRES", "Reptilia" to "REPTILES")
        val all = mutableListOf<Species>()
        for ((higherName, group) in groups) {
            onProgress("Recherche de $higherName…")
            val higher = api.findHigherTaxon(rank = "CLASS", q = higherName).results.firstOrNull { it.taxonomicStatus == "ACCEPTED" } ?: continue
            var offset = 0
            do {
                val page = api.searchSpecies(higherTaxonKey = higher.key, offset = offset)
                page.results.filter { it.rank == "SPECIES" && it.taxonomicStatus == "ACCEPTED" }.forEach { t ->
                    all += Species(t.key, t.vernacularName?.takeIf { it.isNotBlank() } ?: t.canonicalName.orEmpty(), t.canonicalName ?: t.scientificName.orEmpty(), group)
                }
                offset += page.results.size
            } while (!page.endOfRecords && page.results.isNotEmpty())
        }
        val rodentOrder = runCatching { api.findHigherTaxon(rank = "ORDER", q = "Rodentia").results.firstOrNull { it.taxonomicStatus == "ACCEPTED" } }.getOrNull()
        if (rodentOrder != null) {
            var offset = 0
            do {
                val page = api.searchSpecies(higherTaxonKey = rodentOrder.key, offset = offset)
                page.results.filter { it.rank == "SPECIES" && it.taxonomicStatus == "ACCEPTED" }.forEach { t ->
                    all += Species(t.key, t.vernacularName?.takeIf { it.isNotBlank() } ?: t.canonicalName.orEmpty(), t.canonicalName ?: t.scientificName.orEmpty(), "RONGEURS")
                }
                offset += page.results.size
            } while (!page.endOfRecords && page.results.isNotEmpty())
        }
        val unique = all.groupBy { it.id }.values.map { list -> list.firstOrNull { it.group == "RONGEURS" } ?: list.first() }
        db.speciesDao().upsertAll(unique)
        onProgress("${unique.size} espèces chargées")
    }
}

package fr.carnetfaune.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppRepository(private val db: AppDatabase) {

    // ------------------------------------------------------------
    // Base de données
    // ------------------------------------------------------------

    val species: Flow<List<Species>> =
        db.speciesDao().observeAll()

    val places: Flow<List<Place>> =
        db.placeDao().observeAll()

    val observations: Flow<List<Observation>> =
        db.observationDao().observeAll()

    // ------------------------------------------------------------
    // GBIF
    // ------------------------------------------------------------

    private val api = Retrofit.Builder()
        .baseUrl("https://api.gbif.org/v1/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GbifApi::class.java)

    // ------------------------------------------------------------
    // TAXREF
    // ------------------------------------------------------------

    private val taxrefApi = Retrofit.Builder()
        .baseUrl("https://taxref.mnhn.fr/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TaxrefApi::class.java)

    // ------------------------------------------------------------
    // Lieux
    // ------------------------------------------------------------

    suspend fun addPlace(
        name: String,
        lat: Double?,
        lon: Double?,
        habitat: String
    ) = db.placeDao().insert(
        Place(
            name = name.trim(),
            latitude = lat,
            longitude = lon,
            habitat = habitat.trim()
        )
    )

    suspend fun deletePlace(place: Place) =
        db.placeDao().delete(place)

    // ------------------------------------------------------------
    // Observations
    // ------------------------------------------------------------

    suspend fun addObservation(observation: Observation) =
        db.observationDao().insert(observation)

    suspend fun deleteObservation(observation: Observation) =
        db.observationDao().delete(observation)

    suspend fun snapshot() =
        db.observationDao().snapshot()

    // ------------------------------------------------------------
    // Images
    // ------------------------------------------------------------

    suspend fun firstImageUrl(speciesId: Long): String? =
        withContext(Dispatchers.IO) {

            runCatching {
                api.media(speciesId)
                    .results
                    .firstOrNull()
                    ?.identifier
            }
                .recoverCatching {
                    api.occurrenceWithImage(speciesId)
                        .results
                        .firstOrNull()
                        ?.media
                        ?.firstOrNull()
                        ?.identifier
                }
                .getOrNull()
        }

    // ------------------------------------------------------------
    // NOM FRANÇAIS TAXREF
    // ------------------------------------------------------------

    private suspend fun frenchName(
        scientificName: String
    ): String = withContext(Dispatchers.IO) {

        // 1. Recherche du nom scientifique dans TAXREF
        val taxon = runCatching {

            taxrefApi
                .fuzzyMatch(scientificName)
                .embedded
                ?.taxa
                ?.firstOrNull()

        }.getOrNull()

        // 2. Recherche du meilleur nom français
        val frenchName = taxon
            ?.vernacularNames
            ?.firstOrNull {
                it.language.equals("fr", ignoreCase = true) &&
                    !it.name.isNullOrBlank()
            }
            ?.name
            ?.trim()

        if (!frenchName.isNullOrBlank()) {
            return@withContext frenchName
        }

        // 3. Certains résultats TAXREF peuvent avoir directement
        //    vernacularName
        val directName = taxon
            ?.vernacularName
            ?.takeIf { it.isNotBlank() }
            ?.trim()

        if (!directName.isNullOrBlank()) {
            return@withContext directName
        }

        // 4. Si TAXREF ne possède pas de nom vernaculaire,
        //    on garde le nom scientifique comme dernier recours.
        scientificName
    }

    // ------------------------------------------------------------
    // SYNCHRONISATION DU CATALOGUE
    // ------------------------------------------------------------

    suspend fun syncCatalog(
        onProgress: (String) -> Unit
    ) = withContext(Dispatchers.IO) {

        val groups = listOf(
            "Aves" to "OISEAUX",
            "Mammalia" to "MAMMIFÈRES",
            "Reptilia" to "REPTILES"
        )

        val all = mutableListOf<Species>()

        // --------------------------------------------------------
        // OISEAUX / MAMMIFÈRES / REPTILES
        // --------------------------------------------------------

        for ((higherName, group) in groups) {

            onProgress("Recherche de $higherName…")

            val higher = runCatching {
                api.findHigherTaxon(
                    rank = "CLASS",
                    q = higherName
                )
                    .results
                    .firstOrNull {
                        it.taxonomicStatus == "ACCEPTED"
                    }
            }.getOrNull()

            if (higher == null) {
                continue
            }

            var offset = 0

            do {

                val page = api.searchSpecies(
                    higherTaxonKey = higher.key,
                    offset = offset
                )

                val acceptedSpecies = page.results.filter {
                    it.rank == "SPECIES" &&
                        it.taxonomicStatus == "ACCEPTED"
                }

                for (taxon in acceptedSpecies) {

                    val scientificName =
                        taxon.canonicalName
                            ?.takeIf { it.isNotBlank() }
                            ?: taxon.scientificName
                                ?.takeIf { it.isNotBlank() }
                            ?: continue

                    onProgress(
                        "Chargement : $scientificName"
                    )

                    val commonName = frenchName(
                        scientificName
                    )

                    all += Species(
                        id = taxon.key,
                        commonName = commonName,
                        scientificName = scientificName,
                        group = group
                    )
                }

                offset += page.results.size

            } while (
                !page.endOfRecords &&
                page.results.isNotEmpty()
            )
        }

        // --------------------------------------------------------
        // RONGEURS
        // --------------------------------------------------------

        onProgress("Recherche des rongeurs…")

        val rodentOrder = runCatching {
            api.findHigherTaxon(
                rank = "ORDER",
                q = "Rodentia"
            )
                .results
                .firstOrNull {
                    it.taxonomicStatus == "ACCEPTED"
                }
        }.getOrNull()

        if (rodentOrder != null) {

            var offset = 0

            do {

                val page = api.searchSpecies(
                    higherTaxonKey = rodentOrder.key,
                    offset = offset
                )

                val acceptedSpecies = page.results.filter {
                    it.rank == "SPECIES" &&
                        it.taxonomicStatus == "ACCEPTED"
                }

                for (taxon in acceptedSpecies) {

                    val scientificName =
                        taxon.canonicalName
                            ?.takeIf { it.isNotBlank() }
                            ?: taxon.scientificName
                                ?.takeIf { it.isNotBlank() }
                            ?: continue

                    onProgress(
                        "Chargement : $scientificName"
                    )

                    val commonName = frenchName(
                        scientificName
                    )

                    all += Species(
                        id = taxon.key,
                        commonName = commonName,
                        scientificName = scientificName,
                        group = "RONGEURS"
                    )
                }

                offset += page.results.size

            } while (
                !page.endOfRecords &&
                page.results.isNotEmpty()
            )
        }

        // --------------------------------------------------------
        // SUPPRESSION DES DOUBLONS
        // --------------------------------------------------------

        val unique =
            all
                .groupBy { it.id }
                .values
                .map { list ->

                    // Si une espèce apparaît dans plusieurs groupes,
                    // on privilégie RONGEURS.
                    list.firstOrNull {
                        it.group == "RONGEURS"
                    } ?: list.first()
                }

        // --------------------------------------------------------
        // ENREGISTREMENT DANS ROOM
        // --------------------------------------------------------

        db.speciesDao().upsertAll(unique)

        onProgress(
            "${unique.size} espèces chargées"
        )
    }
}

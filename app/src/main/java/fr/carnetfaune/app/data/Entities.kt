package fr.carnetfaune.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "species")
data class Species(
    @PrimaryKey val id: Long,
    val commonName: String,
    val scientificName: String,
    val group: String,
    val imageUrl: String? = null,
    val sourceUrl: String? = null
)

@Entity(tableName = "places")
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val habitat: String = ""
)

@Entity(tableName = "observations")
data class Observation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val speciesId: Long,
    val placeId: Long,
    val date: String,
    val time: String,
    val count: Int = 1,
    val note: String = "",
    val temperatureC: Double? = null,
    val weather: String = "",
    val habitat: String = "",
    val behavior: String = "",
    val sex: String = "",
    val lifeStage: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val photoUri: String? = null
)

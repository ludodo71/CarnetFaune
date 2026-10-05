package fr.carnetfaune.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface SpeciesDao {
    @Query("SELECT * FROM species ORDER BY commonName") fun observeAll(): Flow<List<Species>>
    @Query("SELECT * FROM species WHERE commonName LIKE '%' || :q || '%' OR scientificName LIKE '%' || :q || '%' ORDER BY commonName") fun search(q: String): Flow<List<Species>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<Species>)
    @Query("DELETE FROM species") suspend fun clear()
}

@Dao interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY name") fun observeAll(): Flow<List<Place>>
    @Insert suspend fun insert(place: Place): Long
    @Delete suspend fun delete(place: Place)
}

@Dao interface ObservationDao {
    @Query("SELECT * FROM observations ORDER BY date DESC, time DESC") fun observeAll(): Flow<List<Observation>>
    @Query("SELECT * FROM observations WHERE speciesId=:speciesId AND placeId=:placeId ORDER BY date DESC, time DESC") fun observeCell(speciesId: Long, placeId: Long): Flow<List<Observation>>
    @Insert suspend fun insert(item: Observation): Long
    @Delete suspend fun delete(item: Observation)
    @Query("SELECT * FROM observations") suspend fun snapshot(): List<Observation>
}

@Database(entities = [Species::class, Place::class, Observation::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun speciesDao(): SpeciesDao
    abstract fun placeDao(): PlaceDao
    abstract fun observationDao(): ObservationDao
}

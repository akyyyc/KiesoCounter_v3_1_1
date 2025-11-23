package com.example.kiesocounter_v3_1_1

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Entity(tableName = "number_entries")
data class NumberEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val value: Int,
    val categoryName: String,
    val timestamp: Date
)

@Dao
interface NumberEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: NumberEntry)

    @Update
    suspend fun update(entry: NumberEntry)

    @Delete
    suspend fun delete(entry: NumberEntry)

    @Query("SELECT * FROM number_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<NumberEntry>>

    @Query("SELECT * FROM number_entries WHERE timestamp BETWEEN :startOfDay AND :endOfDay ORDER BY timestamp DESC")
    fun getEntriesForDay(startOfDay: Date, endOfDay: Date): Flow<List<NumberEntry>>

    @Query("SELECT * FROM number_entries WHERE timestamp BETWEEN :startOfMonth AND :endOfMonth ORDER BY timestamp ASC")
    fun getEntriesForMonth(startOfMonth: Date, endOfMonth: Date): Flow<List<NumberEntry>>

    @Query("SELECT * FROM number_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): NumberEntry?

    @Query("SELECT * FROM number_entries ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastEntry(): NumberEntry?

    @Query("DELETE FROM number_entries WHERE timestamp >= :startOfDay")
    suspend fun deleteEntriesSince(startOfDay: Date)

    @Query("DELETE FROM number_entries")
    suspend fun deleteAll()
}

@Database(entities = [NumberEntry::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun numberEntryDao(): NumberEntryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kieso_counter_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

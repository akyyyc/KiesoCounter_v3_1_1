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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Entity(tableName = "number_entries")
data class NumberEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val value: Int,
    val categoryName: String,
    val subCategory: String? = null,
    val movedFrom: String? = null,
    val movedFromGroup: Boolean = false,  // ← ÚJ MEZŐ!
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

    @Query("""
    SELECT DISTINCT date(timestamp / 1000, 'unixepoch', 'localtime') as date 
    FROM number_entries 
    WHERE strftime('%Y-%m', date(timestamp / 1000, 'unixepoch', 'localtime')) = :yearMonth
    """)
    suspend fun getDaysWithDataInMonth(yearMonth: String): List<String>

    @Query("SELECT DISTINCT subCategory FROM number_entries WHERE categoryName = 'Egyéb' AND subCategory IS NOT NULL")
    suspend fun getSubCategoriesForEgyeb(): List<String>
}

// ========== MÓDOSÍTVA: Version 4 → 5, MIGRATION_4_5 hozzáadva ==========

@Database(
    entities = [NumberEntry::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun numberEntryDao(): NumberEntryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE number_entries ADD COLUMN subCategory TEXT")
                database.execSQL("ALTER TABLE number_entries ADD COLUMN movedFrom TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS egyeb_groups (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        groupName TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL DEFAULT 0
                    )
                """)

                val cursor = database.query("""
                    SELECT DISTINCT subCategory 
                    FROM number_entries 
                    WHERE subCategory IS NOT NULL 
                    ORDER BY subCategory
                """)

                var sortOrder = 0
                while (cursor.moveToNext()) {
                    val groupName = cursor.getString(0)
                    val now = System.currentTimeMillis()
                    database.execSQL("""
                        INSERT INTO egyeb_groups (groupName, createdAt, sortOrder) 
                        VALUES ('$groupName', $now, $sortOrder)
                    """)
                    sortOrder++
                }
                cursor.close()
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS egyeb_groups")
            }
        }
        // ========== ÚJ MIGRATION 4 → 5 ==========
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // movedFromGroup mező hozzáadása, alapértelmezett = 0 (false)
                database.execSQL("ALTER TABLE number_entries ADD COLUMN movedFromGroup INTEGER NOT NULL DEFAULT 0")
            }
        }
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kieso_counter_database"
                )
                    // ========== MIGRATION_4_5 hozzáadva a listához ==========
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
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
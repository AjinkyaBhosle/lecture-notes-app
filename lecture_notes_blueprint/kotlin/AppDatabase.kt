package com.yourapp.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Minimal Room DB schema for the lecture app.
 * Existing UI screens (TranscriptionScreen, NotesScreen) point their ViewModels at these DAOs.
 */

@Entity(tableName = "lectures")
data class Lecture(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val audioPath: String,
    val durationMs: Long,
    val createdAt: Long,
    val summary: String? = null,
    val notesMarkdown: String? = null,
    val flashcardsJson: String? = null
)

@Entity(
    tableName = "segments",
    foreignKeys = [ForeignKey(entity = Lecture::class, parentColumns = ["id"],
        childColumns = ["lectureId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("lectureId")]
)
data class Segment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lectureId: Long,
    val startMs: Long,
    val endMs: Long,
    val speakerName: String,
    val text: String
)

@Entity(tableName = "speakers")
data class Speaker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val embedding: FloatArray            // stored via a TypeConverter to ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Speaker) return false
        return id == other.id && name == other.name && embedding.contentEquals(other.embedding)
    }
    override fun hashCode(): Int = 31 * id.hashCode() + name.hashCode()
}

@Dao
interface LectureDao {
    @Insert suspend fun insert(l: Lecture): Long
    @Update suspend fun update(l: Lecture)
    @Query("SELECT * FROM lectures ORDER BY createdAt DESC") fun observeAll(): Flow<List<Lecture>>
    @Query("SELECT * FROM lectures WHERE id = :id") suspend fun getById(id: Long): Lecture?
    @Query("DELETE FROM lectures WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface SegmentDao {
    @Insert suspend fun insertAll(segs: List<Segment>)
    @Query("SELECT * FROM segments WHERE lectureId = :lectureId ORDER BY startMs")
    fun observeByLecture(lectureId: Long): Flow<List<Segment>>
    @Query("SELECT * FROM segments WHERE lectureId = :lectureId ORDER BY startMs")
    suspend fun getByLecture(lectureId: Long): List<Segment>
    @Update suspend fun update(seg: Segment)
    @Query("UPDATE segments SET speakerName = :newName WHERE lectureId = :lectureId AND speakerName = :oldName")
    suspend fun renameSpeaker(lectureId: Long, oldName: String, newName: String)
}

@Dao
interface SpeakerDao {
    @Insert suspend fun insert(s: Speaker): Long
    @Query("SELECT * FROM speakers") suspend fun getAll(): List<Speaker>
    @Query("DELETE FROM speakers WHERE id = :id") suspend fun delete(id: Long)
}

class Converters {
    @TypeConverter fun floatArrayToBytes(a: FloatArray?): ByteArray? {
        if (a == null) return null
        val bb = java.nio.ByteBuffer.allocate(a.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        a.forEach { bb.putFloat(it) }
        return bb.array()
    }
    @TypeConverter fun bytesToFloatArray(b: ByteArray?): FloatArray? {
        if (b == null) return null
        val bb = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(b.size / 4)
        for (i in out.indices) out[i] = bb.float
        return out
    }
}

@Database(entities = [Lecture::class, Segment::class, Speaker::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lectureDao(): LectureDao
    abstract fun segmentDao(): SegmentDao
    abstract fun speakerDao(): SpeakerDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(ctx: android.content.Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext,
                AppDatabase::class.java, "lecture_notes.db").build().also { instance = it }
        }
    }
}

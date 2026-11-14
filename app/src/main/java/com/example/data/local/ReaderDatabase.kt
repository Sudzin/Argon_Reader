package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderDao {
    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC")
    fun observeAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books")
    suspend fun getAllBooksOnce(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBookById(bookId: Long): BookEntity?

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    fun observeBookById(bookId: Long): Flow<BookEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query(
        """
        UPDATE books 
        SET currentParagraphIndex = :paragraphIndex, 
            progressPercent = :progressPercent, 
            lastOpenedAt = :timestamp 
        WHERE id = :bookId
        """
    )
    suspend fun updateReadingProgress(
        bookId: Long,
        paragraphIndex: Int,
        progressPercent: Float,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE books SET readingTimeSeconds = readingTimeSeconds + :deltaSeconds WHERE id = :bookId")
    suspend fun addReadingTime(bookId: Long, deltaSeconds: Long)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBookById(bookId: Long)

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY paragraphIndex ASC")
    fun observeBookmarksForBook(bookId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks")
    suspend fun getAllBookmarksOnce(): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmarkById(bookmarkId: Long)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId AND paragraphIndex = :paragraphIndex")
    suspend fun deleteBookmarkAtParagraph(bookId: Long, paragraphIndex: Int)
}

@Database(
    entities = [BookEntity::class, BookmarkEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun readerDao(): ReaderDao

    companion object {
        @Volatile
        private var INSTANCE: ReaderDatabase? = null

        fun getInstance(context: Context): ReaderDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ReaderDatabase::class.java,
                    "reader_offline.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

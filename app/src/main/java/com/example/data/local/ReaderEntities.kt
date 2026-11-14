package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val filePath: String,
    val originalFileName: String,
    val coverPath: String? = null,
    val format: String,
    val encoding: String = "UTF-8",
    val totalParagraphs: Int,
    val currentParagraphIndex: Int = 0,
    val progressPercent: Float = 0f,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val readingTimeSeconds: Long = 0L,
    val coverColorSeed: Int = 0
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"])]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val paragraphIndex: Int,
    val chapterTitle: String,
    val snippet: String,
    val note: String = "",
    val progressPercent: Float,
    val createdAt: Long = System.currentTimeMillis()
)

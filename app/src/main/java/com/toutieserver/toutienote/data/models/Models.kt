package com.toutieserver.toutienote.data.models

data class Note(
    val id:         String,
    val title:      String,
    val content:    String,
    val updatedAt:  String,
    val createdAt:  String = updatedAt,
    val isHidden:   Boolean = false,
    val isPinned:   Boolean = false,
    val isFavorite: Boolean = false,
    val isLocked:   Boolean = false,
    val colorTag:   String? = null,
    val tags:       List<String> = emptyList(),
)

data class TagSummary(
    val name: String,
    val noteCount: Int,
)

data class NoteAttachment(
    val id: String,
    val filename: String,
    val url: String,
    val mediaType: String,
    val size: Long,
    val createdAt: String,
)

data class Photo(
    val id:            String,
    val filename:      String,
    val url:           String,
    val size:          Long,
    val createdAt:     String,
    val albumId:       String? = null,
    val thumbnailUrl:  String  = url,
    val mediaType:     String  = "image",
    val favorite:      Boolean = false,
)

data class Album(
    val id:         String,
    val name:       String,
    val coverUrl:   String?,
    val createdAt:  String,
    val photoCount: Int,
    val isLocked:   Boolean = false,
    val sortOrder:  Int     = 0,
)

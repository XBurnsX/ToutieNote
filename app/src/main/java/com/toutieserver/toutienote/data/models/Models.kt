package com.toutieserver.toutienote.data.models

data class Note(
    val id: String,
    val title: String,
    val content: String,
    val updatedAt: String,
)

data class Photo(
    val id: String,
    val filename: String,
    val url: String,
    val size: Long,
    val createdAt: String,
    val albumId: String? = null,
    val thumbnailUrl: String = url,
    val mediaType: String = "image",
    val favorite: Boolean = false,
)

data class Album(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val createdAt: String,
    val photoCount: Int,
    val isLocked: Boolean = false,  // Ajouté pour le PIN individuel
    val sortOrder: Int = 0          // Ajouté pour l'ordre manuel
)

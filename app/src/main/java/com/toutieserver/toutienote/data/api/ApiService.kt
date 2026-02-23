package com.toutieserver.toutienote.data.api

import com.toutieserver.toutienote.config.Config
import com.toutieserver.toutienote.data.models.Album
import com.toutieserver.toutienote.data.models.Note
import com.toutieserver.toutienote.data.models.Photo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object ApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val base = Config.BASE_URL

    private fun executeForBody(req: okhttp3.Request): String {
        val response = client.newCall(req).execute()
        if (!response.isSuccessful) {
            throw java.io.IOException("HTTP ${response.code}: ${response.message}")
        }
        val body = response.body ?: throw java.io.IOException("Réponse vide")
        return body.string()
    }

    private fun executeForOk(req: okhttp3.Request) {
        val response = client.newCall(req).execute()
        if (!response.isSuccessful) {
            throw java.io.IOException("HTTP ${response.code}: ${response.message}")
        }
    }

    // ── Notes ──────────────────────────────────────────────────
    fun getNotes(): List<Note> {
        val req = okhttp3.Request.Builder().url("$base/api/notes").get().build()
        val body = executeForBody(req)
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Note(obj.getString("id"), obj.optString("title"), obj.optString("content"), obj.optString("updated_at"))
        }
    }

    fun createNote(title: String, content: String): Note {
        val json = JSONObject().put("title", title).put("content", content).toString()
        val req = okhttp3.Request.Builder().url("$base/api/notes")
            .post(json.toRequestBody(JSON)).build()
        val body = executeForBody(req)
        val obj = JSONObject(body)
        return Note(obj.getString("id"), obj.optString("title"), obj.optString("content"), obj.optString("updated_at"))
    }

    fun updateNote(id: String, title: String, content: String) {
        val json = JSONObject().put("title", title).put("content", content).toString()
        val req = okhttp3.Request.Builder().url("$base/api/notes/$id")
            .put(json.toRequestBody(JSON)).build()
        executeForOk(req)
    }

    fun deleteNote(id: String) {
        val req = okhttp3.Request.Builder().url("$base/api/notes/$id").delete().build()
        executeForOk(req)
    }

    // ── Vault PIN ──────────────────────────────────────────────
    fun pinExists(): Boolean {
        val req = okhttp3.Request.Builder().url("$base/api/vault/pin-exists").get().build()
        val body = executeForBody(req)
        return JSONObject(body).getBoolean("exists")
    }

    fun setupPin(pin: String) {
        val json = JSONObject().put("pin", pin).toString()
        val req = okhttp3.Request.Builder().url("$base/api/vault/pin-setup")
            .post(json.toRequestBody(JSON)).build()
        executeForOk(req)
    }

    fun verifyPin(pin: String): Boolean {
        return try {
            val json = JSONObject().put("pin", pin).toString()
            val req = okhttp3.Request.Builder().url("$base/api/vault/verify")
                .post(json.toRequestBody(JSON)).build()
            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) { false }
    }

    // ── Vault Albums ───────────────────────────────────────────
    private fun optNullableString(obj: JSONObject, key: String): String? {
        return if (obj.isNull(key)) null else obj.optString(key)
    }

    fun getAlbums(): List<Album> {
        val req = okhttp3.Request.Builder().url("$base/api/vault/albums").get().build()
        val body = executeForBody(req)
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Album(
                id = obj.getString("id"),
                name = obj.getString("name"),
                coverUrl = optNullableString(obj, "cover_url"),
                createdAt = obj.optString("created_at"),
                photoCount = obj.optInt("photo_count", 0),
                isLocked = obj.optBoolean("is_locked", false),
                sortOrder = obj.optInt("sort_order", 0)
            )
        }
    }

    fun createAlbum(name: String): Album {
        val json = JSONObject().put("name", name).toString()
        val req = okhttp3.Request.Builder().url("$base/api/vault/albums")
            .post(json.toRequestBody(JSON)).build()
        val body = executeForBody(req)
        val obj = JSONObject(body)
        return Album(
            id = obj.getString("id"),
            name = obj.getString("name"),
            coverUrl = optNullableString(obj, "cover_url"),
            createdAt = obj.optString("created_at"),
            photoCount = obj.optInt("photo_count", 0),
            isLocked = obj.optBoolean("is_locked", false),
            sortOrder = obj.optInt("sort_order", 0)
        )
    }

    fun deleteAlbum(albumId: String) {
        val req = okhttp3.Request.Builder().url("$base/api/vault/albums/$albumId").delete().build()
        executeForOk(req)
    }

    fun renameAlbum(albumId: String, name: String) {
        val json = JSONObject().put("name", name).toString()
        val req = okhttp3.Request.Builder().url("$base/api/vault/albums/$albumId")
            .put(json.toRequestBody(JSON)).build()
        executeForOk(req)
    }

    fun setAlbumCover(albumId: String, photoUrl: String) {
        val json = JSONObject().put("photo_url", photoUrl).toString()
        val req = okhttp3.Request.Builder().url("$base/api/vault/albums/$albumId/cover")
            .put(json.toRequestBody(JSON)).build()
        executeForOk(req)
    }
    
    // NOUVEAU: Fonctions pour verrouiller un album individuel
    fun lockAlbum(albumId: String, pin: String) {
        val json = JSONObject().put("pin", pin).toString()
        val req = okhttp3.Request.Builder().url("$base/api/vault/albums/$albumId/lock")
            .post(json.toRequestBody(JSON)).build()
        executeForOk(req)
    }

    fun verifyAlbumLock(albumId: String, pin: String): Boolean {
        return try {
            val json = JSONObject().put("pin", pin).toString()
            val req = okhttp3.Request.Builder().url("$base/api/vault/albums/$albumId/verify-lock")
                .post(json.toRequestBody(JSON)).build()
            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) { false }
    }

    fun unlockAlbum(albumId: String, pin: String): Boolean {
        return try {
            val json = JSONObject().put("pin", pin).toString()
            val req = okhttp3.Request.Builder().url("$base/api/vault/albums/$albumId/unlock")
                .post(json.toRequestBody(JSON)).build()
            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) { false }
    }

    fun reorderAlbums(albumIds: List<String>) {
        val arr = org.json.JSONArray(albumIds)
        val json = JSONObject().put("album_ids", arr).toString()
        val req = okhttp3.Request.Builder().url("$base/api/vault/albums/reorder")
            .put(json.toRequestBody(JSON)).build()
        executeForOk(req)
    }

    // ── Vault Photos ───────────────────────────────────────────
    fun getPhotos(albumId: String? = null): List<Photo> {
        val url = if (albumId != null) {
            "$base/api/vault/photos?album_id=$albumId"
        } else {
            "$base/api/vault/photos"
        }
        val req = okhttp3.Request.Builder().url(url).get().build()
        val body = executeForBody(req)
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val basePhotoUrl = obj.getString("url")
            Photo(
                id = obj.getString("id"),
                filename = obj.getString("filename"),
                url = basePhotoUrl,
                size = obj.optLong("size"),
                createdAt = obj.optString("created_at"),
                albumId = optNullableString(obj, "album_id"),
                thumbnailUrl = obj.optString("thumbnail_url", basePhotoUrl),
                mediaType = obj.optString("media_type", "image"),
                favorite = (obj.optInt("favorite", 0) == 1),
            )
        }
    }

    data class UploadResult(val photoId: String, val duplicateOf: String?)

    fun uploadPhoto(file: File, filename: String, albumId: String? = null): UploadResult {
        val mimeType = when {
            filename.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            filename.endsWith(".mov", ignoreCase = true) -> "video/quicktime"
            filename.endsWith(".webp", ignoreCase = true) -> "image/webp"
            filename.endsWith(".png", ignoreCase = true) -> "image/png"
            else -> "image/jpeg"
        }
        
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", filename, file.asRequestBody(mimeType.toMediaType()))

        val url = if (albumId != null) {
            "$base/api/vault/upload?album_id=$albumId"
        } else {
            "$base/api/vault/upload"
        }

        val req = okhttp3.Request.Builder().url(url).post(bodyBuilder.build()).build()
        val body = executeForBody(req)
        val obj = JSONObject(body)
        return UploadResult(
            photoId = obj.getString("id"),
            duplicateOf = optNullableString(obj, "duplicate_of")
        )
    }

    /**
     * Remplace le fichier d'une photo existante.
     * Le backend garde le même id, album_id et created_at → la photo ne bouge pas de position.
     */
    fun replacePhoto(photoId: String, file: File, filename: String): Photo {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", filename, file.asRequestBody("image/*".toMediaType()))
            .build()

        val req = okhttp3.Request.Builder()
            .url("$base/api/vault/photo/$photoId/replace")
            .put(body)
            .build()
        val respBody = executeForBody(req)
        val obj = JSONObject(respBody)
        val basePhotoUrl = obj.getString("url")
        return Photo(
            id = obj.getString("id"),
            filename = obj.getString("filename"),
            url = basePhotoUrl,
            size = 0L,
            createdAt = obj.optString("created_at"),
            albumId = optNullableString(obj, "album_id"),
            thumbnailUrl = obj.optString("thumbnail_url", basePhotoUrl),
            mediaType = obj.optString("media_type", "image")
        )
    }

    fun movePhotoToAlbum(photoId: String, albumId: String) {
        val json = JSONObject().put("album_id", albumId).toString()
        val req = okhttp3.Request.Builder().url("$base/api/vault/photo/$photoId/move")
            .put(json.toRequestBody(JSON)).build()
        executeForOk(req)
    }

    data class ScanResult(val groups: List<List<Photo>>, val scanned: Int)

    fun scanDuplicates(albumId: String? = null): ScanResult {
        val url = if (albumId != null) "$base/api/vault/scan-duplicates?album_id=$albumId"
                  else "$base/api/vault/scan-duplicates"
        val req = okhttp3.Request.Builder().url(url)
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()
        val body = executeForBody(req)
        val root = JSONObject(body)
        val arr = root.getJSONArray("groups")
        val groups = mutableListOf<List<Photo>>()
        for (i in 0 until arr.length()) {
            val groupArr = arr.getJSONArray(i)
            val group = (0 until groupArr.length()).map { j ->
                val obj = groupArr.getJSONObject(j)
                val basePhotoUrl = obj.getString("url")
                Photo(
                    id = obj.getString("id"),
                    filename = obj.getString("filename"),
                    url = basePhotoUrl,
                    size = obj.optLong("size"),
                    createdAt = obj.optString("created_at"),
                    albumId = optNullableString(obj, "album_id"),
                    thumbnailUrl = obj.optString("thumbnail_url", basePhotoUrl),
                    mediaType = obj.optString("media_type", "image")
                )
            }
            groups.add(group)
        }
        return ScanResult(groups, root.optInt("scanned", 0))
    }

    fun toggleFavorite(photoId: String): Boolean {
        val req = okhttp3.Request.Builder().url("$base/api/vault/photo/$photoId/favorite")
            .put("{}".toRequestBody(JSON)).build()
        val body = executeForBody(req)
        return JSONObject(body).optBoolean("favorite", false)
    }

    fun deletePhoto(filename: String) {
        val req = okhttp3.Request.Builder().url("$base/api/vault/photo/$filename").delete().build()
        executeForOk(req)
    }

    fun resizePhoto(filename: String, width: Int, height: Int) {
        val req = okhttp3.Request.Builder()
            .url("$base/api/vault/resize/$filename?width=$width&height=$height")
            .post("".toRequestBody()).build()
        executeForOk(req)
    }

    fun downloadPhotoBytes(photoUrl: String): ByteArray {
        val fullUrl = photoUrl(photoUrl)
        val req = okhttp3.Request.Builder().url(fullUrl).get().build()
        val response = client.newCall(req).execute()
        if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
        return response.body?.bytes() ?: throw java.io.IOException("Body vide")
    }

    fun photoUrl(url: String): String {
        val path = url.trimStart('/')
        val baseNoSlash = base.trimEnd('/')
        return "$baseNoSlash/$path"
    }
}

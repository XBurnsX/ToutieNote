package com.toutieserver.toutienote.data.api

import android.util.Log
import com.toutieserver.toutienote.config.Config
import com.toutieserver.toutienote.data.auth.AuthRepository
import com.toutieserver.toutienote.data.models.Album
import com.toutieserver.toutienote.data.models.Note
import com.toutieserver.toutienote.data.models.Photo
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object ApiService {

    private val authInterceptor = Interceptor { chain ->
        val token = AuthRepository.getToken()
        val req = chain.request().newBuilder()
        if (!token.isNullOrBlank()) {
            req.addHeader("Authorization", "Bearer $token")
        }
        chain.proceed(req.build())
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
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

    private fun optNullableString(obj: JSONObject, key: String): String? {
        return if (obj.isNull(key)) null else obj.optString(key)
    }

    private fun noteFromJson(obj: JSONObject): Note {
        return Note(
            id         = obj.getString("id"),
            title      = obj.optString("title"),
            content    = obj.optString("content"),
            updatedAt  = obj.optString("updated_at"),
            isHidden   = obj.optInt("is_hidden",   0) == 1,
            isPinned   = obj.optInt("is_pinned",   0) == 1,
            isFavorite = obj.optInt("is_favorite", 0) == 1,
            isLocked   = obj.optBoolean("is_locked", false),
            colorTag   = optNullableString(obj, "color_tag"),
        )
    }

    // ── Auth ────────────────────────────────────────────────────
    data class AuthResponse(val token: String, val user_id: String, val username: String)

    fun register(username: String, password: String): AuthResponse {
        val json = JSONObject().put("username", username).put("password", password).toString()
        val req = okhttp3.Request.Builder().url("$base/api/auth/register")
            .post(json.toRequestBody(JSON)).build()
        val body = executeForBody(req)
        val obj = JSONObject(body)
        return AuthResponse(obj.getString("token"), obj.getString("user_id"), obj.getString("username"))
    }

    fun login(username: String, password: String): AuthResponse {
        val json = JSONObject().put("username", username).put("password", password).toString()
        val req = okhttp3.Request.Builder().url("$base/api/auth/login")
            .post(json.toRequestBody(JSON)).build()
        val body = executeForBody(req)
        val obj = JSONObject(body)
        return AuthResponse(obj.getString("token"), obj.getString("user_id"), obj.getString("username"))
    }

    // ── Notes ──────────────────────────────────────────────────
    fun getNotes(): List<Note> {
        val req = okhttp3.Request.Builder().url("$base/api/notes").get().build()
        val body = executeForBody(req)
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i -> noteFromJson(arr.getJSONObject(i)) }
    }

    fun getNoteById(id: String): Note {
        val req = okhttp3.Request.Builder().url("$base/api/notes/$id").get().build()
        val body = executeForBody(req)
        return noteFromJson(JSONObject(body))
    }

    fun searchNotes(q: String): List<Note> {
        val req = okhttp3.Request.Builder()
            .url("$base/api/notes/search?q=${java.net.URLEncoder.encode(q, "UTF-8")}")
            .get().build()
        val body = executeForBody(req)
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Note(id = obj.getString("id"), title = obj.optString("title"),
                 content = "", updatedAt = obj.optString("updated_at"))
        }
    }

    fun getBacklinks(noteId: String): List<Note> {
        val req = okhttp3.Request.Builder().url("$base/api/notes/$noteId/backlinks").get().build()
        val body = executeForBody(req)
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Note(id = obj.getString("id"), title = obj.optString("title"),
                 content = "", updatedAt = obj.optString("updated_at"))
        }
    }

    fun createNote(title: String, content: String, hidden: Boolean = false): Note {
        val json = JSONObject().put("title", title).put("content", content).toString()
        val url = if (hidden) "$base/api/notes?hidden=true" else "$base/api/notes"
        val req = okhttp3.Request.Builder().url(url).post(json.toRequestBody(JSON)).build()
        val body = executeForBody(req)
        return noteFromJson(JSONObject(body))
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

    // ── Notes — Favoris & Épingler ─────────────────────────────
    data class ToggleResult(val value: Boolean)

    fun toggleNoteFavorite(id: String): Boolean {
        val req = okhttp3.Request.Builder().url("$base/api/notes/$id/favorite")
            .put("{}".toRequestBody(JSON)).build()
        val body = executeForBody(req)
        return JSONObject(body).optBoolean("is_favorite", false)
    }

    fun toggleNotePin(id: String): Boolean {
        val req = okhttp3.Request.Builder().url("$base/api/notes/$id/pin")
            .put("{}".toRequestBody(JSON)).build()
        val body = executeForBody(req)
        return JSONObject(body).optBoolean("is_pinned", false)
    }

    // ── Notes — Lock ───────────────────────────────────────────
    fun lockNote(id: String, pin: String) {
        val json = JSONObject().put("pin", pin).toString()
        val req = okhttp3.Request.Builder().url("$base/api/notes/$id/lock")
            .post(json.toRequestBody(JSON)).build()
        executeForOk(req)
    }

    fun unlockNote(id: String, pin: String): Boolean {
        return try {
            val json = JSONObject().put("pin", pin).toString()
            val req = okhttp3.Request.Builder().url("$base/api/notes/$id/unlock")
                .post(json.toRequestBody(JSON)).build()
            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) { false }
    }

    fun removeNoteLock(id: String, pin: String): Boolean {
        return try {
            val json = JSONObject().put("pin", pin).toString()
            val req = okhttp3.Request.Builder().url("$base/api/notes/$id/remove-lock")
                .post(json.toRequestBody(JSON)).build()
            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) { false }
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

    fun changePin(oldPin: String, newPin: String) {
        val json = JSONObject().put("old_pin", oldPin).put("new_pin", newPin).toString()
        val req = okhttp3.Request.Builder().url("$base/api/vault/change-pin")
            .post(json.toRequestBody(JSON)).build()
        executeForOk(req)
    }

    // ── Vault Albums ───────────────────────────────────────────
    fun getAlbums(): List<Album> {
        val req = okhttp3.Request.Builder().url("$base/api/vault/albums").get().build()
        val body = executeForBody(req)
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Album(
                id         = obj.getString("id"),
                name       = obj.getString("name"),
                coverUrl   = optNullableString(obj, "cover_url"),
                createdAt  = obj.optString("created_at"),
                photoCount = obj.optInt("photo_count", 0),
                isLocked   = obj.optBoolean("is_locked", false),
                sortOrder  = obj.optInt("sort_order", 0)
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
            id         = obj.getString("id"),
            name       = obj.getString("name"),
            coverUrl   = optNullableString(obj, "cover_url"),
            createdAt  = obj.optString("created_at"),
            photoCount = obj.optInt("photo_count", 0),
            isLocked   = obj.optBoolean("is_locked", false),
            sortOrder  = obj.optInt("sort_order", 0)
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

    fun reorderPhotos(albumId: String, photoIds: List<String>) {
        val arr = org.json.JSONArray(photoIds)
        val json = JSONObject().put("photo_ids", arr).toString()
        val req = okhttp3.Request.Builder().url("$base/api/vault/albums/$albumId/photos/reorder")
            .put(json.toRequestBody(JSON)).build()
        executeForOk(req)
    }

    // ── Vault Photos ───────────────────────────────────────────
    fun getPhotos(albumId: String? = null): List<Photo> {
        val url = if (albumId != null) "$base/api/vault/photos?album_id=$albumId"
                  else "$base/api/vault/photos"
        val req = okhttp3.Request.Builder().url(url).get().build()
        val body = executeForBody(req)
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val basePhotoUrl = obj.getString("url")
            Photo(
                id           = obj.getString("id"),
                filename     = obj.getString("filename"),
                url          = basePhotoUrl,
                size         = obj.optLong("size"),
                createdAt    = obj.optString("created_at"),
                albumId      = optNullableString(obj, "album_id"),
                thumbnailUrl = obj.optString("thumbnail_url", basePhotoUrl),
                mediaType    = obj.optString("media_type", "image"),
                favorite     = (obj.optInt("favorite", 0) == 1),
            )
        }
    }

    data class UploadResult(val photoId: String, val duplicateOf: String?)

    fun uploadPhoto(file: File, filename: String, albumId: String? = null): UploadResult {
        val mimeType = when {
            filename.endsWith(".mp4",  ignoreCase = true) -> "video/mp4"
            filename.endsWith(".mov",  ignoreCase = true) -> "video/quicktime"
            filename.endsWith(".webp", ignoreCase = true) -> "image/webp"
            filename.endsWith(".png",  ignoreCase = true) -> "image/png"
            else -> "image/jpeg"
        }
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", filename, file.asRequestBody(mimeType.toMediaType()))
        val url = if (albumId != null) "$base/api/vault/upload?album_id=$albumId"
                  else "$base/api/vault/upload"
        val req = okhttp3.Request.Builder().url(url).post(bodyBuilder.build()).build()
        val body = executeForBody(req)
        val obj = JSONObject(body)
        return UploadResult(
            photoId     = obj.getString("id"),
            duplicateOf = optNullableString(obj, "duplicate_of")
        )
    }

    fun replacePhoto(photoId: String, file: File, filename: String): Photo {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", filename, file.asRequestBody("image/*".toMediaType()))
            .build()
        val req = okhttp3.Request.Builder()
            .url("$base/api/vault/photo/$photoId/replace")
            .put(body).build()
        val respBody = executeForBody(req)
        val obj = JSONObject(respBody)
        val basePhotoUrl = obj.getString("url")
        return Photo(
            id           = obj.getString("id"),
            filename     = obj.getString("filename"),
            url          = basePhotoUrl,
            size         = 0L,
            createdAt    = obj.optString("created_at"),
            albumId      = optNullableString(obj, "album_id"),
            thumbnailUrl = obj.optString("thumbnail_url", basePhotoUrl),
            mediaType    = obj.optString("media_type", "image")
        )
    }

    fun movePhotoToAlbum(photoId: String, albumId: String) {
        val json = JSONObject().put("album_id", albumId).toString()
        val req = okhttp3.Request.Builder().url("$base/api/vault/photo/$photoId/move")
            .put(json.toRequestBody(JSON)).build()
        executeForOk(req)
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

    // ── Duplicate Scan ─────────────────────────────────────────
    data class ScanResult(val groups: List<List<Photo>>, val scanned: Int)
    data class ScanStatus(val scanned: Int, val total: Int, val percent: Int,
                          val done: Boolean, val groups: List<List<Photo>>, val error: String?)

    private fun parsePhotoFromJson(obj: JSONObject): Photo {
        val basePhotoUrl = obj.getString("url")
        return Photo(
            id           = obj.getString("id"),
            filename     = obj.getString("filename"),
            url          = basePhotoUrl,
            size         = obj.optLong("size"),
            createdAt    = obj.optString("created_at"),
            albumId      = optNullableString(obj, "album_id"),
            thumbnailUrl = obj.optString("thumbnail_url", basePhotoUrl),
            mediaType    = obj.optString("media_type", "image"),
            favorite     = obj.optInt("favorite", 0) == 1
        )
    }

    fun getPhotoCount(albumId: String? = null): Int {
        val url = if (albumId != null) "$base/api/vault/photo-count?album_id=$albumId"
                  else "$base/api/vault/photo-count"
        val req = okhttp3.Request.Builder().url(url).get().build()
        return JSONObject(executeForBody(req)).optInt("count", 0)
    }

    fun startScanAsync(albumId: String? = null): Pair<String, Int> {
        val url = if (albumId != null) "$base/api/vault/scan-duplicates?album_id=$albumId"
                  else "$base/api/vault/scan-duplicates"
        val req = okhttp3.Request.Builder().url(url)
            .post(okhttp3.RequestBody.create(null, ByteArray(0))).build()
        val body = executeForBody(req)
        val root = JSONObject(body)
        return Pair(root.getString("job_id"), root.optInt("total", 0))
    }

    fun scanDuplicatesSync(albumId: String? = null): ScanResult {
        val url = if (albumId != null) "$base/api/vault/scan-duplicates-sync?album_id=$albumId"
                  else "$base/api/vault/scan-duplicates-sync"
        val req = okhttp3.Request.Builder().url(url)
            .post(okhttp3.RequestBody.create(null, ByteArray(0))).build()
        val body = executeForBody(req)
        val root = JSONObject(body)
        val groups = mutableListOf<List<Photo>>()
        if (root.has("groups")) {
            val arr = root.getJSONArray("groups")
            for (i in 0 until arr.length()) {
                val groupArr = arr.getJSONArray(i)
                groups.add((0 until groupArr.length()).map { j ->
                    parsePhotoFromJson(groupArr.getJSONObject(j))
                })
            }
        }
        return ScanResult(groups, root.optInt("scanned", 0))
    }

    fun getScanStatus(jobId: String): ScanStatus {
        val req = okhttp3.Request.Builder()
            .url("$base/api/vault/scan-duplicates/status?job_id=$jobId").get().build()
        val body = executeForBody(req)
        val root = JSONObject(body)
        val groups = mutableListOf<List<Photo>>()
        if (root.has("groups")) {
            val arr = root.getJSONArray("groups")
            for (i in 0 until arr.length()) {
                val groupArr = arr.getJSONArray(i)
                groups.add((0 until groupArr.length()).map { j ->
                    parsePhotoFromJson(groupArr.getJSONObject(j))
                })
            }
        }
        return ScanStatus(
            scanned = root.optInt("scanned", 0),
            total   = root.optInt("total", 0),
            percent = root.optInt("percent", 0),
            done    = root.optBoolean("done", false),
            groups  = groups,
            error   = if (root.isNull("error")) null else root.getString("error")
        )
    }

    // ── Helpers ─────────────────────────────────────────────────
    fun photoUrl(url: String): String {
        val path = url.trimStart('/')
        val baseNoSlash = base.trimEnd('/')
        return "$baseNoSlash/$path"
    }
}

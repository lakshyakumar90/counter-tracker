package com.pace.app.data.sync

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class PaceCloudHttpException(val statusCode: Int, val retryable: Boolean) : IOException("Pace cloud request failed with HTTP $statusCode.")
class PaceBackupQuotaExceededException : IOException("Pace backup storage quota would be exceeded.")

/** Small authenticated PostgREST/Storage transport. Tokens and payloads are never logged. */
class SupabaseBackupClient(private val projectUrl: String, private val publishableKey: String) {
    init {
        require(projectUrl.startsWith("https://") || projectUrl.startsWith("http://10.0.2.2")) { "Supabase URL must use HTTPS (or the Android local development host)." }
        require(publishableKey.isNotBlank())
    }

    suspend fun upsert(table: String, token: String, row: JSONObject) = withContext(Dispatchers.IO) {
        request("POST", "/rest/v1/$table?on_conflict=${conflictColumn(table)}", token, row.toString().toByteArray(), "application/json", "resolution=merge-duplicates,return=minimal")
    }

    suspend fun markDeleted(table: String, token: String, idColumn: String, id: String, deletedAtEpochMillis: Long) = withContext(Dispatchers.IO) {
        val safeId = URLEncoder.encode(id, Charsets.UTF_8.name())
        request(
            "PATCH", "/rest/v1/$table?$idColumn=eq.$safeId", token,
            JSONObject().put("deleted_at_epoch_millis", deletedAtEpochMillis).put("updated_at_epoch_millis", deletedAtEpochMillis).toString().toByteArray(),
            "application/json", "return=minimal",
        )
    }

    suspend fun deleteTrack(token: String, userId: String, activityId: String) = withContext(Dispatchers.IO) {
        require(userId.matches(Regex("[0-9a-fA-F-]{36}")) && activityId.matches(Regex("[0-9a-fA-F-]{36}")))
        val path = "$userId/$activityId.pace.gz"
        val encoded = path.split('/').joinToString("/") { URLEncoder.encode(it, Charsets.UTF_8.name()) }
        try {
            request("DELETE", "/storage/v1/object/pace-private-tracks/$encoded", token, null, "", "")
        } catch (missing: PaceCloudHttpException) {
            if (missing.statusCode != 404) throw missing
        }
    }

    suspend fun deleteRoute(token: String, userId: String, routeId: String) = withContext(Dispatchers.IO) {
        require(userId.matches(Regex("[0-9a-fA-F-]{36}")) && routeId.matches(Regex("[0-9a-fA-F-]{36}")))
        val path = "$userId/routes/$routeId.pace.gz"
        val encoded = path.split('/').joinToString("/") { URLEncoder.encode(it, Charsets.UTF_8.name()) }
        try {
            request("DELETE", "/storage/v1/object/pace-private-tracks/$encoded", token, null, "", "")
        } catch (missing: PaceCloudHttpException) {
            if (missing.statusCode != 404) throw missing
        }
    }

    suspend fun fetchPage(table: String, token: String, offset: Int, limit: Int = 500): JSONArray = withContext(Dispatchers.IO) {
        require(table in BACKUP_TABLES && offset >= 0 && limit in 1..500)
        val body = request(
            "GET", "/rest/v1/$table?select=*&order=updated_at_epoch_millis.asc&limit=$limit&offset=$offset",
            token, null, "", "", maxResponseBytes = 10 * 1024 * 1024,
        )
        JSONArray(body.toString(Charsets.UTF_8))
    }

    suspend fun backupPreview(token: String): JSONObject = withContext(Dispatchers.IO) {
        val body = request(
            "POST", "/rest/v1/rpc/pace_backup_preview", token, "{}".toByteArray(),
            "application/json", "", maxResponseBytes = 64 * 1024,
        )
        JSONObject(body.toString(Charsets.UTF_8))
    }

    suspend fun checkArchiveQuota(token: String, userId: String, objectPath: String, archiveBytes: Int) = withContext(Dispatchers.IO) {
        require(userId.matches(Regex("[0-9a-fA-F-]{36}")))
        require(objectPath.matches(Regex("${Regex.escape(userId)}/(?:[0-9a-fA-F-]{36}\\.pace\\.gz|routes/[0-9a-fA-F-]{36}\\.pace\\.gz)")))
        require(archiveBytes in 1..100 * 1024 * 1024)
        val body = request(
            "POST", "/rest/v1/rpc/pace_backup_upload_check", token,
            JSONObject().put("p_object_path", objectPath).put("p_archive_bytes", archiveBytes).toString().toByteArray(),
            "application/json", "", maxResponseBytes = 64 * 1024,
        )
        if (!JSONObject(body.toString(Charsets.UTF_8)).optBoolean("allowed", false)) {
            throw PaceBackupQuotaExceededException()
        }
    }

    suspend fun downloadTrack(token: String, userId: String, objectPath: String): ByteArray = withContext(Dispatchers.IO) {
        require(userId.matches(Regex("[0-9a-fA-F-]{36}")))
        require(objectPath.matches(Regex("${Regex.escape(userId)}/(?:[0-9a-fA-F-]{36}\\.pace\\.gz|routes/[0-9a-fA-F-]{36}\\.pace\\.gz)")))
        val encoded = objectPath.split('/').joinToString("/") { URLEncoder.encode(it, Charsets.UTF_8.name()) }
        request("GET", "/storage/v1/object/pace-private-tracks/$encoded", token, null, "", "", maxResponseBytes = 100 * 1024 * 1024)
    }

    private fun request(
        method: String,
        path: String,
        token: String,
        body: ByteArray?,
        contentType: String,
        prefer: String,
        extraHeaders: Map<String, String> = emptyMap(),
        maxResponseBytes: Int = 1024 * 1024,
    ): ByteArray {
        val connection = (URL(projectUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
            }
            if (prefer.isNotBlank()) setRequestProperty("Prefer", prefer)
            extraHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
            setRequestProperty("User-Agent", "Pace-Android")
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            if (code !in 200..299) throw PaceCloudHttpException(code, code == 408 || code == 425 || code == 429 || code >= 500)
            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxResponseBytes) throw IllegalArgumentException("Cloud response exceeds the configured size limit.")
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun conflictColumn(table: String): String = when (table) {
        "pace_activities" -> "user_id,activity_id"
        "pace_workout_templates" -> "user_id,template_id"
        "pace_planned_sessions" -> "user_id,session_id"
        "pace_goals" -> "user_id,goal_id"
        "pace_personal_records" -> "user_id,record_id"
        "pace_saved_routes" -> "user_id,route_id"
        else -> throw IllegalArgumentException("Unsupported backup table.")
    }

    private companion object {
        val BACKUP_TABLES = setOf("pace_activities", "pace_workout_templates", "pace_planned_sessions", "pace_goals", "pace_personal_records", "pace_saved_routes")
    }
}

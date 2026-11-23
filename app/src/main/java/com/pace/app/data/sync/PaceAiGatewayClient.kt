package com.pace.app.data.sync

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Authenticated provider-neutral app boundary. Provider credentials stay in the Edge Function. */
class PaceAiGatewayClient(private val projectUrl: String, private val publishableKey: String) {
    suspend fun setConsent(accessToken: String, optedIn: Boolean) = withContext(Dispatchers.IO) {
        request("ai-consent", accessToken, JSONObject().put("optedIn", optedIn))
    }

    suspend fun generate(accessToken: String, kind: String, summary: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        require(kind in setOf("insight", "workout"))
        val body = JSONObject().put("kind", kind).put("summary", summary)
        require(body.toString().toByteArray().size <= 16_384) { "AI summary exceeds its size limit." }
        request("ai-insights", accessToken, body)
    }

    suspend fun exportUsage(accessToken: String): JSONArray = withContext(Dispatchers.IO) {
        require(projectUrl.startsWith("https://") && publishableKey.isNotBlank() && accessToken.isNotBlank())
        val url = URL("${projectUrl.trimEnd('/')}/rest/v1/pace_ai_usage?select=id,created_at,kind,input_tokens,output_tokens,estimated_cost_usd,prompt_version,status&order=created_at.desc&limit=1000")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("apikey", publishableKey)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            val code = connection.responseCode
            if (code !in 200..299) throw PaceAiGatewayException("USAGE_EXPORT_FAILED")
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4_096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 512 * 1024) throw IllegalArgumentException("AI usage export exceeds its size limit.")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            JSONArray(bytes.toString(Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }

    private fun request(functionName: String, accessToken: String, body: JSONObject): JSONObject {
        require(projectUrl.startsWith("https://") && publishableKey.isNotBlank() && accessToken.isNotBlank())
        val connection = URL("${projectUrl.trimEnd('/')}/functions/v1/$functionName").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", publishableKey)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val output = ByteArrayOutputStream()
            stream?.use { input ->
                val buffer = ByteArray(4_096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 32_768) throw IllegalArgumentException("AI gateway response exceeds its size limit.")
                    output.write(buffer, 0, count)
                }
            }
            val response = runCatching { JSONObject(output.toString(Charsets.UTF_8.name())) }.getOrElse { JSONObject() }
            if (code !in 200..299) throw PaceAiGatewayException(response.optString("error", "AI_UNAVAILABLE"))
            return response
        } finally {
            connection.disconnect()
        }
    }
}

class PaceAiGatewayException(val safeCode: String) : Exception("Pace AI gateway request failed: $safeCode")

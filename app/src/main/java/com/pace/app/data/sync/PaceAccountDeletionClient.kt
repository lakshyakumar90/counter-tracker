package com.pace.app.data.sync

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Calls the authenticated server deletion function; the service-role key never enters the app. */
class PaceAccountDeletionClient(private val projectUrl: String, private val publishableKey: String) {
    suspend fun deleteAccount(accessToken: String) = withContext(Dispatchers.IO) {
        require(projectUrl.startsWith("https://") && publishableKey.isNotBlank() && accessToken.isNotBlank())
        val connection = URL("${projectUrl.trimEnd('/')}/functions/v1/delete-account").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", publishableKey)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write("{}".toByteArray()) }
            if (connection.responseCode !in 200..299) throw IllegalStateException("Account deletion service did not complete successfully.")
        } finally {
            connection.disconnect()
        }
    }
}

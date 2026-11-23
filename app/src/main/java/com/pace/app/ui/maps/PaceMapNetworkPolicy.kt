package com.pace.app.ui.maps

import com.pace.app.BuildConfig
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.maplibre.android.module.http.HttpRequestUtil

/** Gives the map provider a recognizable client and honors its parallel-request limit. */
object PaceMapNetworkPolicy {
    @Volatile private var configured = false

    @Synchronized
    fun configure() {
        if (configured) return
        val dispatcher = Dispatcher().apply {
            maxRequests = 4
            maxRequestsPerHost = 4
        }
        HttpRequestUtil.setOkHttpClient(
            OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "Pace-Android/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
                        .build()
                    chain.proceed(request)
                }
                .build(),
        )
        configured = true
    }
}

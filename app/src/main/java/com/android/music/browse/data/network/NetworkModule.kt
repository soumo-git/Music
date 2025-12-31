package com.android.music.browse.data.network

import com.android.music.BuildConfig
import com.android.music.browse.data.api.SpotifyApiService
import com.android.music.browse.data.api.YouTubeApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Network module providing Retrofit instances for API calls.
 * Implements singleton pattern for efficient resource usage.
 */
object NetworkModule {

    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT = 30L
    private const val WRITE_TIMEOUT = 30L
    
    var oauthToken: String? = null
    var spotifyOauthToken: String? = null

    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    private val youtubeOkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("Accept", "application/json")
                
                // Add OAuth token if available
                oauthToken?.let {
                    requestBuilder.header("Authorization", "Bearer $it")
                }
                
                requestBuilder.method(original.method, original.body)
                chain.proceed(requestBuilder.build())
            }
            .build()
    }

    private val spotifyOkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("Accept", "application/json")
                
                // Add Spotify OAuth token if available
                spotifyOauthToken?.let {
                    requestBuilder.header("Authorization", "Bearer $it")
                }
                
                requestBuilder.method(original.method, original.body)
                chain.proceed(requestBuilder.build())
            }
            .build()
    }

    private val youtubeRetrofit by lazy {
        Retrofit.Builder()
            .baseUrl(YouTubeApiService.BASE_URL)
            .client(youtubeOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val spotifyRetrofit by lazy {
        Retrofit.Builder()
            .baseUrl(SpotifyApiService.BASE_URL)
            .client(spotifyOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val youtubeApiService: YouTubeApiService by lazy {
        youtubeRetrofit.create(YouTubeApiService::class.java)
    }

    val spotifyApiService: SpotifyApiService by lazy {
        spotifyRetrofit.create(SpotifyApiService::class.java)
    }

}

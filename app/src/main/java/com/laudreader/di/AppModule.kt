package com.laudreader.di

import android.content.Context
import com.laudreader.auth.GoogleAuthManager
import com.laudreader.extractor.ArticleExtractor
import com.laudreader.tts.TtsEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideGoogleAuthManager(@ApplicationContext context: Context): GoogleAuthManager {
        return GoogleAuthManager(context)
    }

    @Provides
    @Singleton
    fun provideArticleExtractor(okHttpClient: OkHttpClient): ArticleExtractor {
        return ArticleExtractor(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideTtsEngine(okHttpClient: OkHttpClient, authManager: GoogleAuthManager): TtsEngine {
        return TtsEngine(okHttpClient, authManager)
    }
}

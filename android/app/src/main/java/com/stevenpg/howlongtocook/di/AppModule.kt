package com.stevenpg.howlongtocook.di

import android.content.Context
import androidx.room.Room
import com.stevenpg.howlongtocook.data.local.AppDatabase
import com.stevenpg.howlongtocook.data.local.ProductDao
import com.stevenpg.howlongtocook.data.settings.KeystoreSecretStore
import com.stevenpg.howlongtocook.data.settings.SecretStore
import com.stevenpg.howlongtocook.data.sync.SyncRequester
import com.stevenpg.howlongtocook.data.sync.SyncScheduler
import com.stevenpg.howlongtocook.util.AppJson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun productDao(db: AppDatabase): ProductDao = db.productDao()

    @Provides
    @Singleton
    fun httpClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(30, TimeUnit.SECONDS)
                followRedirects(false)
            }
        }
        install(HttpTimeout) { requestTimeoutMillis = 60_000 }
        install(ContentNegotiation) { json(AppJson) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun secretStore(impl: KeystoreSecretStore): SecretStore

    @Binds
    abstract fun syncRequester(impl: SyncScheduler): SyncRequester
}

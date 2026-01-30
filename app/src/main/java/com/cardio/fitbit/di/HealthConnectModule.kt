package com.cardio.fitbit.di

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.cardio.fitbit.data.provider.HealthConnectProvider
import com.cardio.fitbit.data.provider.HealthDataProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HealthConnectModule {

    @Provides
    @Singleton
    fun provideHealthConnectClient(
        @ApplicationContext context: Context
    ): HealthConnectClient {
        return HealthConnectClient.getOrCreate(context)
    }

    @Provides
    @Singleton
    fun provideHealthDataProvider(
        healthConnectClient: HealthConnectClient
    ): HealthConnectProvider { // Keeping concrete type here for any other specific injections? 
        return HealthConnectProvider(healthConnectClient)
    }

    @Provides
    @Singleton
    fun provideHealthDataProviderInterface(
        provider: HealthConnectProvider
    ): HealthDataProvider {
        return provider
    }
}

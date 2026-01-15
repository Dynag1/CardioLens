package com.cardio.fitbit.di

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.cardio.fitbit.data.provider.HealthConnectProvider
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
    fun provideHealthConnectProvider(
        healthConnectClient: HealthConnectClient
    ): HealthConnectProvider {
        return HealthConnectProvider(healthConnectClient)
    }
}

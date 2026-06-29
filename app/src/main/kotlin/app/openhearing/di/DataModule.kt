package app.openhearing.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.openhearing.data.DataStoreProfileRepository
import app.openhearing.data.DataStoreSettingsRepository
import app.openhearing.data.ProfileRepository
import app.openhearing.data.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the persistence layer (DataStore-backed settings + profiles). */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile("openhearing")
    }

    @Provides
    @Singleton
    fun settingsRepository(dataStore: DataStore<Preferences>): SettingsRepository =
        DataStoreSettingsRepository(dataStore)

    @Provides
    @Singleton
    fun profileRepository(dataStore: DataStore<Preferences>): ProfileRepository = DataStoreProfileRepository(dataStore)
}

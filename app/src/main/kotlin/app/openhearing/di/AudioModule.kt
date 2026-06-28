package app.openhearing.di

import app.openhearing.audiogram.FittingStrategy
import app.openhearing.audiogram.halfGainFitting
import app.openhearing.core.audio.ToneGenerator
import app.openhearing.core.audio.TonePlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the audio + fitting collaborators used by the hearing-test screen. */
@Module
@InstallIn(SingletonComponent::class)
object AudioModule {
    @Provides
    @Singleton
    fun toneGenerator(): ToneGenerator = ToneGenerator()

    // Not a singleton: each consumer gets its own AudioTrack-backed player and is
    // responsible for releasing it.
    @Provides
    fun tonePlayer(): TonePlayer = TonePlayer()

    @Provides
    @Singleton
    fun fittingStrategy(): FittingStrategy = halfGainFitting()
}

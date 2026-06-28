package app.openhearing

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point; Hilt's dependency graph is rooted here. */
@HiltAndroidApp
class OpenHearingApplication : Application()

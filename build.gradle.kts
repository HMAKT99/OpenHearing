// Root build script. Plugins are declared here with `apply false` so that each
// subproject can apply them from the shared version catalog without re-declaring
// versions. Module-specific configuration lives in each module's build.gradle.kts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.android.junit5) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

// Apply code-quality plugins to every module so `./gradlew detekt ktlintCheck`
// covers the whole tree.
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    detekt {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        // Phase 0 keeps the build green while we grow the codebase; tighten later.
        ignoreFailures = false
    }

    ktlint {
        version.set("1.4.1")
        android.set(true)
        ignoreFailures.set(false)
    }
}

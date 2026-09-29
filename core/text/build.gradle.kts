plugins {
    alias(libs.plugins.app.jvm.library)
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.bundles.unit.test)
}

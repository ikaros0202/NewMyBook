plugins {
    alias(libs.plugins.app.android.feature)
}

android {
    namespace = "com.xinyue.reader.feature.importing"
}

dependencies {
    implementation(libs.bundles.compose)
    implementation(project(":core:text"))
    testImplementation(libs.bundles.unit.test)
}

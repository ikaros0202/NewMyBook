plugins {
    alias(libs.plugins.app.android.feature)
}

android {
    namespace = "com.xinyue.reader.feature.settings"
}

dependencies {
    implementation(libs.bundles.compose)
    testImplementation(libs.bundles.unit.test)
}

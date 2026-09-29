plugins {
    alias(libs.plugins.app.android.feature)
}

android {
    namespace = "com.xinyue.reader.feature.home"
}

dependencies {
    implementation(libs.bundles.compose)
    testImplementation(libs.bundles.unit.test)
}

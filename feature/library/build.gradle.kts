plugins {
    alias(libs.plugins.app.android.feature)
}

android {
    namespace = "com.xinyue.reader.feature.library"
}

dependencies {
    implementation(libs.bundles.compose)
    testImplementation(libs.bundles.unit.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.hilt)
    alias(libs.plugins.app.kotlin.serialization)
}

android {
    namespace = "com.xinyue.reader.core.data"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:text"))
    implementation(project(":core:database"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.apache.commons.compress)
    implementation(libs.androidx.work.runtime)
    implementation(libs.hilt.work)
    implementation(libs.room3.runtime)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.bundles.unit.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.sqlite.bundled)
    androidTestImplementation(libs.room3.testing)
    androidTestImplementation(libs.bundles.unit.test)
}

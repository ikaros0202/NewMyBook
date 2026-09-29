import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "app.android.library")
        apply(plugin = "app.android.library.compose")
        apply(plugin = "app.hilt")
        dependencies {
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:data"))
            add("implementation", project(":core:ui"))
            add("implementation", libs.findLibrary("androidx.activity.compose").get())
            add("implementation", libs.findLibrary("androidx.core.ktx").get())
            add("implementation", libs.findLibrary("androidx.lifecycle.runtime.compose").get())
            add("implementation", libs.findLibrary("androidx.lifecycle.viewmodel.compose").get())
            add("implementation", libs.findLibrary("hilt.lifecycle.viewmodel.compose").get())
            add("implementation", libs.findLibrary("androidx.navigation3.runtime").get())
            add("implementation", libs.findLibrary("androidx.navigation3.ui").get())
        }
    }
}

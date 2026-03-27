dependencies {
    api(projects.frameworkApi)

    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.core)
}

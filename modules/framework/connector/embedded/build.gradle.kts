dependencies {
    api(projects.sdkKotlinApi)
    api(projects.frameworkApi)

    implementation(projects.frameworkImplementationInMemory)
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.core)
}

dependencies {
    api(projects.projectionDomain)
    api(projects.eventStoreDomain)

    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.core)
}

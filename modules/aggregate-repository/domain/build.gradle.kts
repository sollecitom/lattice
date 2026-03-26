dependencies {
    api(projects.aggregateDomain)
    api(projects.eventStoreDomain)
    api(projects.recoveryStoreDomain)
    api(platform(libs.kotlinx.coroutines.bom))
    api(libs.kotlinx.coroutines.core)
}

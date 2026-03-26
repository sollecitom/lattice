dependencies {
    api(projects.aggregateRepositoryDomain)
    api(projects.aggregateDomain)
    api(projects.eventStoreDomain)
    api(projects.recoveryStoreDomain)

    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.core)
}

dependencies {
    api(projects.aggregateDomain)
    api(projects.aggregateRepositoryDomain)
    api(platform(libs.kotlinx.coroutines.bom))
    api(libs.kotlinx.coroutines.core)
}

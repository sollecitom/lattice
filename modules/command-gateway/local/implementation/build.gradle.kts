dependencies {
    api(projects.commandGatewayDomain)
    api(projects.aggregateRepositoryDomain)

    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.core)
}

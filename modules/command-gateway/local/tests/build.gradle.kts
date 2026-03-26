dependencies {
    testImplementation(projects.commandGatewayLocalImplementation)
    testImplementation(projects.commandGatewayTestSpecification)
    testImplementation(projects.aggregateRepositoryDefaultImplementation)
    testImplementation(projects.eventStoreInMemoryImplementation)
    testImplementation(projects.aggregateDomain)
    testImplementation(projects.aggregateTestUtils)
    testImplementation(libs.sollecitom.swissknife.test.utils)
    testImplementation(libs.sollecitom.swissknife.logging.standard.slf4j.configuration)
}

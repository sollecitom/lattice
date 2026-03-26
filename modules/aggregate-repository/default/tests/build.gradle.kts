dependencies {
    testImplementation(projects.aggregateRepositoryDefaultImplementation)
    testImplementation(projects.aggregateRepositoryTestSpecification)
    testImplementation(projects.aggregateRepositoryTestUtils)
    testImplementation(projects.eventStoreInMemoryImplementation)
    testImplementation(projects.recoveryStoreInMemoryImplementation)
    testImplementation(projects.aggregateDomain)
    testImplementation(projects.aggregateTestUtils)
    testImplementation(libs.sollecitom.swissknife.test.utils)
    testImplementation(libs.sollecitom.swissknife.logging.standard.slf4j.configuration)
}

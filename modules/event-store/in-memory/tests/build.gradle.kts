dependencies {
    testImplementation(projects.eventStoreInMemoryImplementation)
    testImplementation(projects.eventStoreTestSpecification)
    testImplementation(projects.eventStoreTestUtils)
    testImplementation(projects.aggregateTestUtils)
    testImplementation(libs.sollecitom.swissknife.test.utils)
    testImplementation(libs.sollecitom.swissknife.logging.standard.slf4j.configuration)
}

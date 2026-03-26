dependencies {
    testImplementation(projects.projectionInMemoryImplementation)
    testImplementation(projects.projectionTestSpecification)
    testImplementation(projects.projectionDomain)
    testImplementation(projects.eventStoreDomain)
    testImplementation(projects.aggregateTestUtils)
    testImplementation(libs.sollecitom.swissknife.test.utils)
    testImplementation(libs.sollecitom.swissknife.logging.standard.slf4j.configuration)
}

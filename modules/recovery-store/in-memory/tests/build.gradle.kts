dependencies {
    testImplementation(projects.recoveryStoreInMemoryImplementation)
    testImplementation(projects.recoveryStoreTestSpecification)
    testImplementation(projects.recoveryStoreTestUtils)
    testImplementation(projects.aggregateTestUtils)
    testImplementation(libs.sollecitom.swissknife.test.utils)
    testImplementation(libs.sollecitom.swissknife.logging.standard.slf4j.configuration)
}

dependencies {
    api(projects.eventStoreDomain)
    api(projects.aggregateTestUtils)
    api(projects.eventStoreTestUtils)
    api(libs.sollecitom.swissknife.test.utils)

    implementation(libs.sollecitom.swissknife.kotlin.extensions)
}

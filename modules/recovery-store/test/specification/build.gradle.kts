dependencies {
    api(projects.recoveryStoreDomain)
    api(projects.aggregateTestUtils)
    api(projects.recoveryStoreTestUtils)
    api(libs.sollecitom.swissknife.test.utils)

    implementation(libs.sollecitom.swissknife.kotlin.extensions)
}

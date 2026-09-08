plugins {
    id("sollecitom.kotlin-library-conventions")
}

dependencies {
    testImplementation(projects.companySdk)
    testImplementation(projects.companyTestUtils)
    testImplementation(platform(libs.kotlinx.coroutines.bom))
    testImplementation(libs.kotlinx.coroutines.core)
}

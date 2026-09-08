// Semantically faithful in-memory implementation (D31): real partitions, offsets, and lag.
plugins {
    id("sollecitom.kotlin-library-conventions")
    id("sollecitom.maven-publish-conventions")
}

dependencies {
    api(projects.frameworkCore)

    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.sollecitom.swissknife.test.utils)
}

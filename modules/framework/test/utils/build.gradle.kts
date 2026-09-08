plugins {
    id("sollecitom.kotlin-library-conventions")
    id("sollecitom.maven-publish-conventions")
}

dependencies {
    api(projects.frameworkCore)
    api(projects.frameworkInMemory)
    api(libs.sollecitom.swissknife.test.utils)
}

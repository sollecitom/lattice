plugins {
    id("sollecitom.kotlin-library-conventions")
    id("sollecitom.maven-publish-conventions")
}

dependencies {
    api(projects.sdkKotlinApi)
    api(libs.sollecitom.swissknife.test.utils)
    api(libs.sollecitom.swissknife.core.test.utils)

    implementation(libs.sollecitom.swissknife.kotlin.extensions)
}

plugins {
    id("sollecitom.kotlin-library-conventions")
    id("sollecitom.maven-publish-conventions")
}

dependencies {
    api(libs.sollecitom.swissknife.core.domain)
    api(platform(libs.kotlinx.coroutines.bom))
    api(libs.kotlinx.coroutines.core)
}

plugins {
    id("sollecitom.kotlin-library-conventions")
    id("sollecitom.maven-publish-conventions")
}

dependencies {
    api(projects.sdkKotlinApi)
    api(platform(libs.kotlinx.coroutines.bom))
    api(libs.kotlinx.coroutines.core)
}

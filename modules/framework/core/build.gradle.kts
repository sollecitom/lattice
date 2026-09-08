// The API developers code against. Keep dependencies at zero (coroutines only) — anything else here
// is inherited by every consumer's domain model.
plugins {
    id("sollecitom.kotlin-library-conventions")
    id("sollecitom.maven-publish-conventions")
}

dependencies {
    api(platform(libs.kotlinx.coroutines.bom))
    api(libs.kotlinx.coroutines.core)
}

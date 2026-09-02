// Drives the framework design outside-in: everything here is written from a developer's perspective,
// using only the company stubs and whatever public API the framework exposes.
//
// The framework itself does not exist yet. It gets extracted from this module once the usage has a
// shape worth generalising.
plugins {
    id("sollecitom.kotlin-library-conventions")
}

dependencies {
    testImplementation(projects.companyStubs)
    testImplementation(libs.sollecitom.swissknife.test.utils)
}

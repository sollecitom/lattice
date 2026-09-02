// Stand-in for the types a consuming company would define: domain facts and invocation context.
//
// Invariant (see ../../events-framework.md, D30): this module must compile without importing the
// framework. No dependency on any other module in this repo may be added here.
plugins {
    id("sollecitom.kotlin-library-conventions")
    id("sollecitom.maven-publish-conventions")
}

dependencies {
    testImplementation(libs.sollecitom.swissknife.test.utils)
}

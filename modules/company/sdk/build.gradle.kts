// The company's business-operation SDK over the framework SDK. A second consumer of the framework API,
// and a sharper test of it than the tests are.
plugins {
    id("sollecitom.kotlin-library-conventions")
    id("sollecitom.maven-publish-conventions")
}

dependencies {
    api(projects.companyDomain)
    api(projects.frameworkCore)
}

plugins {
    id("sollecitom.kotlin-library-conventions")
    id("sollecitom.maven-publish-conventions")
}

dependencies {
    testImplementation(projects.sdkKotlinTestSpecification)
    testImplementation(projects.sdkKotlinApi)
    testImplementation(projects.frameworkConnectorEmbedded)
    testImplementation(libs.sollecitom.swissknife.test.utils)
    testImplementation(libs.sollecitom.swissknife.logging.standard.slf4j.configuration)
}

dependencies {
    testImplementation(projects.sdkKotlinTestSpecification)
    testImplementation(projects.sdkKotlinApi)
    testImplementation(projects.frameworkConnectorEmbedded)
    testImplementation(libs.sollecitom.swissknife.test.utils)
    testImplementation(libs.sollecitom.swissknife.logging.standard.slf4j.configuration)
}

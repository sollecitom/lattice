plugins {
    alias(libs.plugins.com.palantir.git.version)
    alias(libs.plugins.sollecitom.dependency.update.conventions)
    alias(libs.plugins.sollecitom.aggregate.test.metrics.conventions)
    alias(libs.plugins.sollecitom.kotlin.library.conventions) apply false
    alias(libs.plugins.sollecitom.maven.publish.conventions) apply false
}

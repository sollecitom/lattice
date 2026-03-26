dependencies {
    api(projects.aggregateDomain)
    api(projects.eventStoreDomain)
    api(libs.sollecitom.swissknife.core.domain)
    api(platform(libs.kotlinx.coroutines.bom))
    api(libs.kotlinx.coroutines.core)
}

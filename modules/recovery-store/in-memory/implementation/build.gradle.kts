dependencies {
    api(projects.recoveryStoreDomain)

    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.core)
}

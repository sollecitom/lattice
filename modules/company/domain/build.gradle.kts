// What a consuming company writes: facts, aggregate, read model. Imports `core` and nothing else (D30).
plugins {
    id("sollecitom.kotlin-library-conventions")
    id("sollecitom.maven-publish-conventions")
}

dependencies {
    api(projects.frameworkCore)
}

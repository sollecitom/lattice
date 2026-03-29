@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("../gradle-plugins")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "lattice"

includeBuild("../swissknife")

fun module(vararg pathSegments: String) = subProject(rootFolder = "modules", pathSegments = pathSegments)

fun subProject(rootFolder: String, vararg pathSegments: String, excludeRootFolderFromGroupName: Boolean = true) {

    val projectName = pathSegments.last()
    val path = listOf(rootFolder) + pathSegments.dropLast(1)
    val group = if (excludeRootFolderFromGroupName) path.minus(rootFolder).joinToString(separator = "-") else path.joinToString(separator = "-", prefix = "${rootProject.name}-")
    val directory = path.joinToString(separator = "/", prefix = "./")
    val fullProjectName = "${if (group.isEmpty()) "" else "$group-"}$projectName"

    include(fullProjectName)
    project(":$fullProjectName").projectDir = mkdir("$directory/$projectName")
}

fun includeProject(name: String) {

    apply("$name/settings.gradle.kts")
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Kotlin SDK
module("sdk", "kotlin", "api")
module("sdk", "kotlin", "test", "specification")
module("sdk", "kotlin", "in-memory", "tests")

// Framework
module("framework", "api")
module("framework", "implementation", "in-memory")
module("framework", "connector", "embedded")

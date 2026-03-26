@file:Suppress("UnstableApiUsage")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "lattice"

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

// Core aggregate domain
module("aggregate", "domain")
module("aggregate", "test", "utils")
module("aggregate", "test", "specification")

// Event store
module("event-store", "domain")
module("event-store", "test", "utils")
module("event-store", "test", "specification")
module("event-store", "in-memory", "implementation")
module("event-store", "in-memory", "tests")

// Recovery store (snapshots)
module("recovery-store", "domain")
module("recovery-store", "test", "utils")
module("recovery-store", "test", "specification")
module("recovery-store", "in-memory", "implementation")
module("recovery-store", "in-memory", "tests")

// Aggregate repository (orchestrates load/decide/persist)
module("aggregate-repository", "domain")
module("aggregate-repository", "test", "utils")
module("aggregate-repository", "test", "specification")
module("aggregate-repository", "default", "implementation")
module("aggregate-repository", "default", "tests")

// Projection (materialized views)
module("projection", "domain")
module("projection", "test", "utils")
module("projection", "test", "specification")
module("projection", "in-memory", "implementation")
module("projection", "in-memory", "tests")

// Command gateway (sync request-reply at the boundary)
module("command-gateway", "domain")
module("command-gateway", "test", "specification")
module("command-gateway", "local", "implementation")
module("command-gateway", "local", "tests")

// Integration tests (end-to-end wiring)
module("integration", "tests")

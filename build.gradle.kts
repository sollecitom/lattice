import sollecitom.plugins.conventions.task.dependency.version.MinimumDependencyVersion
import sollecitom.plugins.conventions.task.dependency.version.MinimumDependencyVersionConventions
import sollecitom.plugins.conventions.task.kotlin.KotlinTaskConventions
import sollecitom.plugins.conventions.task.maven.publish.MavenPublishConvention
import sollecitom.plugins.conventions.task.test.AggregateTestMetricsConventions
import sollecitom.plugins.conventions.task.test.TestTaskConventions
import sollecitom.plugins.RepositoryConfiguration
import sollecitom.plugins.conventions.task.dependency.update.DependencyUpdateConvention
import com.palantir.gradle.gitversion.GitVersionPlugin
import com.palantir.gradle.gitversion.VersionDetails
import groovy.lang.Closure
import sollecitom.plugins.Plugins
import java.nio.file.Path
import java.nio.file.Paths

buildscript {
    repositories {
        mavenLocal()
    }

    dependencies {
        classpath(libs.sollecitom.gradle.plugins.base)
        classpath(libs.sollecitom.gradle.plugins.kotlin.jvm)
    }
}

repositories {
    RepositoryConfiguration.BuildScript.apply(this)
}

plugins {
    `java-library`
    idea
    alias(libs.plugins.com.palantir.git.version)
}

apply<GitVersionPlugin>()
apply<DependencyUpdateConvention>()
apply<KotlinTaskConventions>()

val parentProject = this
val projectGroup: String by properties
val currentVersion: String by properties
val versionDetails: Closure<VersionDetails> by extra
val gitVersion = versionDetails()
val publicationsFolder: Path = rootProject.projectDir.path.let { Paths.get(it) }.resolve("modules")

fun Project.shouldBePublished() = projectDir.path.let { Paths.get(it) }.startsWith(publicationsFolder)

apply<AggregateTestMetricsConventions>()

allprojects {

    group = projectGroup
    version = currentVersion

    project.extra["gitVersion"] = gitVersion

    repositories { RepositoryConfiguration.Modules.apply(this, project) }

    apply<IdeaPlugin>()
    idea { module { inheritOutputDirs = true } }

    apply<KotlinTaskConventions>()
    apply<TestTaskConventions>()

    tasks.withType<Test>().configureEach {
        jvmArgs("-Djava.net.preferIPv4Stack=true")
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    if (shouldBePublished()) {
        apply<MavenPublishConvention>()
    }

    java(Plugins.JavaPlugin::configure)

    apply<MinimumDependencyVersionConventions>()
    configure<MinimumDependencyVersionConventions.Extension> {
        val apacheCommonsCompress = MinimumDependencyVersion(group = "org.apache.commons", name = "commons-compress", minimumVersion = "1.26.0")
        knownVulnerableDependencies.set(
            listOf(
                apacheCommonsCompress
            )
        )
    }

    subprojects {
        apply {
            plugin("org.jetbrains.kotlin.jvm")
            plugin<JavaLibraryPlugin>()
        }
    }
}

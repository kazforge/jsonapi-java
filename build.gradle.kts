import org.cyclonedx.gradle.CyclonedxDirectTask

// Root project — no source code.
// Shared build logic lives in build-logic/ convention plugins.

buildscript {
    dependencies {
        constraints {
            classpath("com.fasterxml.jackson.core:jackson-core:2.22.4") {
                because("CycloneDX 3.5.0 resolves 2.22.2, affected by GHSA-p6pp-m3f8-5c89 and GHSA-7hhh-6rmp-j9qf")
            }
            classpath("com.google.guava:guava:33.7.2-jre") {
                because("CycloneDX tooling must not resolve vulnerable Guava 33.7.1")
            }
        }
    }
}

plugins {
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.cyclonedx)
    id("jsonapi-java-spotless")
}

allprojects {
    tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
        includeConfigs = listOf("compileClasspath", "runtimeClasspath")
        includeMetadataResolution = false
        xmlOutput.unsetConvention()
    }
}

tasks.cyclonedxBom {
    xmlOutput.unsetConvention()
}

sonar {
    properties {
        property("sonar.projectKey", "kazemek_jsonapi-java")
        property("sonar.organization", "kazemek")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.qualitygate.wait", "true")
    }
}

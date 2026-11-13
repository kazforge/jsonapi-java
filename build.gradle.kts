import org.cyclonedx.gradle.CyclonedxDirectTask

// Root project — no source code.
// Shared build logic lives in build-logic/ convention plugins.

buildscript {
    dependencies {
        constraints {
            classpath("com.google.guava:guava:33.8.0-jre") {
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

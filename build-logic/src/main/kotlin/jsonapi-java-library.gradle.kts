import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway

plugins {
    `java-library`
    groovy
    jacoco
    id("net.ltgt.errorprone")
    id("net.ltgt.nullaway")
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withJavadocJar()
}

dependencies {
    compileOnly(libs.findLibrary("jspecify").get())
    errorprone(libs.findLibrary("errorprone-core").get())
    errorprone(libs.findLibrary("nullaway").get())
    testImplementation(libs.findLibrary("spock-core").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

nullaway {
    onlyNullMarked.set(true)
    jspecifyMode.set(true)
}

tasks.named<JavaCompile>("compileJava").configure {
    options.errorprone {
        disableAllChecks.set(true)
        error("NullAway")
        error("RequireExplicitNullMarking")
        nullaway {
            error()
        }
    }
}

plugins.withId("java-test-fixtures") {
    tasks.named<JavaCompile>("compileTestFixturesJava").configure {
        options.errorprone.enabled.set(false)
    }
}

// Test runtime selection: workers default to the Java 21 baseline so a plain local
// build behaves exactly as before. CI passes -PtestJavaVersion=<matrix> to run the
// suite on a newer runtime while compilation stays on the 21 toolchain above.
val testJavaVersion = providers.gradleProperty("testJavaVersion").getOrElse("21")

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(testJavaVersion.toInt()))
        },
    )
}

tasks.withType<Javadoc>().configureEach {
    // Unsupported implementation packages are not part of the consumer documentation surface.
    exclude("**/internal/**")
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("tag", "apiNote:a:API Note:")
        // Keep syntax, reference, HTML, and accessibility checks strict without requiring
        // low-information documentation for every parameter, return value, or declaration.
        addBooleanOption("Xdoclint:all,-missing", true)
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestReport)
    dependsOn(tasks.javadoc)
}

// Fixed repository policy: executable library modules require at least 80% line and branch
// coverage. The annotations module has no executable coverage to verify.
if (project.name != "jsonapi-java-annotations") {
    tasks.jacocoTestCoverageVerification {
        dependsOn(tasks.test)
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    minimum = "0.80".toBigDecimal()
                }
                limit {
                    counter = "BRANCH"
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
    }
    tasks.named("check") {
        dependsOn(tasks.jacocoTestCoverageVerification)
    }
}

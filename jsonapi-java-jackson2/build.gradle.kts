plugins {
    id("jsonapi-java-library")
    id("jsonapi-java-publish")
}

val jacksonTestVersion = providers.gradleProperty("jacksonTestVersion").getOrElse("minimum")
require(jacksonTestVersion in setOf("minimum", "current")) {
    "jacksonTestVersion must be minimum or current"
}

dependencies {
    api(project(":jsonapi-java-api"))
    api(project(":jsonapi-java-annotations"))
    api(project(":jsonapi-java-core"))
    implementation(project(":jsonapi-java-mapping"))
    api(libs.jackson2.databind)
    // Runtime Optional support for the mapping fallback: registered on the derived mapping mapper
    // only when the caller's configured mapper lacks Optional serialization (see JsonApiJackson2).
    implementation(libs.jackson2.jdk8)
    if (jacksonTestVersion == "current") {
        testImplementation(libs.jackson2.current.databind)
        testImplementation(libs.jackson2.current.jdk8)
    }
    testImplementation(testFixtures(project(":jsonapi-java-api")))
    testImplementation(libs.archunit)
    // JSON-only schema checks use this adapter's Jackson, not the validator's requested versions.
    testImplementation(libs.json.schema.validator2) {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.fasterxml.jackson.dataformat")
    }
}

tasks.test {
    systemProperty(
        "jackson.expectedVersion",
        if (jacksonTestVersion == "current") {
            libs.versions.jackson2.current
                .get()
        } else {
            libs.versions.jackson2.minimum
                .get()
        },
    )
}

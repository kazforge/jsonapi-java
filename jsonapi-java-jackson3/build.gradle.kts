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
    api(libs.jackson3.databind)
    if (jacksonTestVersion == "current") {
        testImplementation(libs.jackson3.current.databind)
    }
    testImplementation(testFixtures(project(":jsonapi-java-api")))
    testImplementation(libs.archunit)
    // JSON-only schema checks use this adapter's Jackson, not the validator's requested versions.
    testImplementation(libs.json.schema.validator) {
        exclude(group = "tools.jackson.core")
        exclude(group = "tools.jackson.dataformat")
    }
}

tasks.test {
    systemProperty(
        "jackson.expectedVersion",
        if (jacksonTestVersion == "current") {
            libs.versions.jackson3.current
                .get()
        } else {
            libs.versions.jackson3.minimum
                .get()
        },
    )
}

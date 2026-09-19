plugins {
    id("jsonapi-java-library")
}

dependencies {
    // Spike-only: this module has no production sources and is never published.
    testImplementation(project(":jsonapi-java-core"))
    testImplementation(project(":jsonapi-java-annotations"))
    // Transitional neutral contracts still live in the historically named jackson-api artifact.
    testImplementation(project(":jsonapi-java-jackson-api"))
    testImplementation(project(":jsonapi-java-mapping"))
    testImplementation(testFixtures(project(":jsonapi-java-mapping")))
    testImplementation(libs.gson)
    testCompileOnly(libs.jspecify)
    testImplementation(libs.archunit)
}

plugins {
    id("jsonapi-java-library")
}

dependencies {
    api(project(":jsonapi-java-jackson-api"))
    api(project(":jsonapi-java-annotations"))
    api(project(":jsonapi-java-core"))
    api(libs.jackson2.databind)
    testImplementation(testFixtures(project(":jsonapi-java-jackson-api")))
    testImplementation(libs.archunit)
    testImplementation(libs.json.schema.validator)
}

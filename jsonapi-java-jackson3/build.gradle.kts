plugins {
    id("jsonapi-java-library")
    id("jsonapi-java-publish")
}

dependencies {
    api(project(":jsonapi-java-jackson-api"))
    api(project(":jsonapi-java-annotations"))
    api(project(":jsonapi-java-core"))
    api(libs.jackson3.databind)
    testImplementation(testFixtures(project(":jsonapi-java-jackson-api")))
    testImplementation(libs.archunit)
    testImplementation(libs.json.schema.validator)
}

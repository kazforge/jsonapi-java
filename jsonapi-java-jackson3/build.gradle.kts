plugins {
    id("jsonapi-java-library")
    id("jsonapi-java-publish")
}

dependencies {
    api(project(":jsonapi-java-api"))
    api(project(":jsonapi-java-annotations"))
    api(project(":jsonapi-java-core"))
    implementation(project(":jsonapi-java-mapping"))
    api(libs.jackson3.databind)
    testImplementation(testFixtures(project(":jsonapi-java-api")))
    testImplementation(libs.archunit)
    testImplementation(libs.json.schema.validator)
}

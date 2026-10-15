plugins {
    id("jsonapi-java-library")
    id("jsonapi-java-publish")
    id("java-test-fixtures")
}

dependencies {
    api(project(":jsonapi-java-core"))
    testFixturesImplementation(project(":jsonapi-java-annotations"))
    testFixturesImplementation(libs.jackson.annotations)
    testFixturesCompileOnly(libs.jspecify)
    testImplementation(libs.archunit)
}

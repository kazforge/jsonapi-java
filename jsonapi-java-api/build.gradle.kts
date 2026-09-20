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
    testFixturesImplementation(libs.spock.core)
    testFixturesImplementation(libs.groovy.json)
    testImplementation(libs.archunit)
}

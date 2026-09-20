plugins {
    id("jsonapi-java-library")
    id("jsonapi-java-publish")
}

dependencies {
    api(project(":jsonapi-java-api"))
    testImplementation(libs.archunit)
}

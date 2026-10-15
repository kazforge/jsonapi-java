plugins {
    id("jsonapi-java-library")
    id("jsonapi-java-publish")
}

dependencies {
    api(project(":jsonapi-java-core"))
    testImplementation(libs.archunit)
}

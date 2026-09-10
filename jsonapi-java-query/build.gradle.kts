plugins {
    id("jsonapi-java-library")
}

dependencies {
    api(project(":jsonapi-java-jackson-api"))
    testImplementation(libs.archunit)
}

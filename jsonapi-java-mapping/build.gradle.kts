plugins {
    id("jsonapi-java-library")
    id("jsonapi-java-publish")
}

dependencies {
    implementation(project(":jsonapi-java-api"))
    testCompileOnly(libs.jspecify)
    testImplementation(libs.archunit)
}

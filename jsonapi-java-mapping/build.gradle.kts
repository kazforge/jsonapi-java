plugins {
    id("jsonapi-java-library")
    id("jsonapi-java-publish")
    id("java-test-fixtures")
}

dependencies {
    // Transitional PoC dependency: several mapper-neutral representation and diagnostic contracts
    // still live in jackson-api today. A production extraction should reassess that ownership.
    implementation(project(":jsonapi-java-jackson-api"))

    testFixturesImplementation(project(":jsonapi-java-core"))
    testFixturesImplementation(project(":jsonapi-java-annotations"))
    testImplementation(libs.archunit)
}

plugins {
    id("jsonapi-java-library")
    id("jsonapi-java-publish")
}

dependencies {
    api(project(":jsonapi-java-jackson-api"))
    api(project(":jsonapi-java-annotations"))
    api(project(":jsonapi-java-core"))
    implementation(project(":jsonapi-java-mapping"))
    api(libs.jackson2.databind)
    // Runtime Optional support for the mapping fallback: registered on the derived mapping mapper
    // only when the caller's configured mapper lacks Optional serialization (see JsonApiJackson2).
    implementation(libs.jackson2.jdk8)
    testImplementation(testFixtures(project(":jsonapi-java-jackson-api")))
    testImplementation(testFixtures(project(":jsonapi-java-mapping")))
    testImplementation(libs.archunit)
    testImplementation(libs.json.schema.validator)
    testCompileOnly(libs.jspecify)
}

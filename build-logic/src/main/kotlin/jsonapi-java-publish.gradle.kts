plugins {
    `java-library`
    `maven-publish`
    signing
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set(project.name)
                description.set(
                    when (project.name) {
                        "jsonapi-java-core" -> {
                            "Dependency-free JSON:API document model and validation."
                        }

                        "jsonapi-java-annotations" -> {
                            "Dependency-free domain-mapping role annotations."
                        }

                        "jsonapi-java-api" -> {
                            "Backend-neutral application and capability contracts."
                        }

                        "jsonapi-java-query" -> {
                            "Neutral query-parameter parsing."
                        }

                        "jsonapi-java-jackson3" -> {
                            "Jackson 3 runtime, codec, mapping, and PATCH binding."
                        }

                        "jsonapi-java-jackson2" -> {
                            "Jackson 2 runtime, codec, mapping, and PATCH binding."
                        }

                        else -> {
                            "JSON:API document, mapping, and validation library."
                        }
                    },
                )
                url.set("https://github.com/kazforge/jsonapi-java")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("kazforge")
                        name.set("kazforge")
                        url.set("https://github.com/kazforge")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/kazforge/jsonapi-java.git")
                    developerConnection.set("scm:git:ssh://github.com/kazforge/jsonapi-java.git")
                    url.set("https://github.com/kazforge/jsonapi-java")
                }
            }
        }
    }
    repositories {
        maven {
            name = "centralStaging"
            url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

signing {
    val signingKey = providers.gradleProperty("signingKey")
    val signingPassword = providers.gradleProperty("signingPassword")
    isRequired = signingKey.isPresent
    if (signingKey.isPresent) {
        useInMemoryPgpKeys(signingKey.get(), signingPassword.orElse("").get())
    }
    sign(publishing.publications["mavenJava"])
}

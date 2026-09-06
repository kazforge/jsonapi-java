// Root project — no source code.
// Shared build logic lives in build-logic/ convention plugins.

plugins {
    alias(libs.plugins.sonarqube)
    id("jsonapi-java-spotless")
}

sonar {
    properties {
        property("sonar.projectKey", "kazemek_jsonapi-java")
        property("sonar.organization", "kazemek")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.qualitygate.wait", "true")
        // Intentional Jackson 2/Jackson 3 document-reader parity: these Jackson 2 production
        // files are token-driven adaptations of their Jackson 3 counterparts, kept adapter-local
        // for Jackson-major isolation. Tracked for reassessment by KAZ-115.
        property(
            "sonar.cpd.exclusions",
            """
            src/main/java/io/github/kazemek/jsonapi/jackson2/JsonApiDocumentReader.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/DocumentWireReader.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/ErrorWireReader.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/JsonApiWireReader.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/JsonPointerAccumulator.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/LinkWireReader.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/MemberClassifier.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/PointerEscapes.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/ReadLocationIndex.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/ReadLocations.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/ResourceWireReader.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/ValidationPointers.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/WireObjectMembers.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/WireTokens.java
            """.trimIndent(),
        )
    }
}

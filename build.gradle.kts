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
        // Intentional Jackson 2/Jackson 3 parity duplication: these Jackson 2 production files are
        // adapter-local adaptations of their Jackson 3 counterparts (document codec and read-side
        // first; now also the write-side domain mapping engine), kept adapter-local for
        // Jackson-major isolation per ADR-007. Reassess for cross-major consolidation only if a
        // Jackson-major-neutral mechanism is ever accepted; do not exclude whole packages.
        property(
            "sonar.cpd.exclusions",
            """
            src/main/java/io/github/kazemek/jsonapi/jackson2/JsonApiDocumentReader.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/JsonApiResourceMapper.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/CompoundInclusionEngine.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/DocumentWireReader.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/DomainResourceWriter.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/ErrorWireReader.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/JsonApiWireReader.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/JsonPointerAccumulator.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/LinkWireReader.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/MappingDefinitionResolver.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/MemberClassifier.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/MetaBindingModule.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/PointerEscapes.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/PropertyScopedValueConverter.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/RawValueBeanPropertyWriter.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/RawValuePropertyModule.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/ReadLocationIndex.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/ReadLocations.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/RelationshipLinkageSupport.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/ResourceWireReader.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/ResolvedTypeSupport.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/ValidationPointers.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/WireObjectMembers.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/WireTokens.java,
            src/main/java/io/github/kazemek/jsonapi/jackson2/internal/WholeMetaTarget.java
            """.trimIndent(),
        )
    }
}

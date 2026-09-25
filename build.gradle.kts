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
        // Exclude only substantial Jackson 2 implementations paired with Jackson 3: token-level
        // readers; configured-Jackson construction, property discovery, conversion, and module
        // bridges; and the adapter's document, resource, and PATCH operation coordinators. These
        // remain separately compiled to preserve native codec and mapper authority (ADR-022).
        // Neutral mapping orchestration lives in jsonapi-java-mapping. The paired public typed
        // envelope types stay excluded because metaAs(JavaType) is Jackson-major. Small codec
        // utilities, property records, and thin public delegates participate in CPD; do not exclude
        // packages.
        // Paths must be repository-root-relative; module-relative `src/main/java/...` at project
        // level is deprecated by SonarCloud.
        property(
            "sonar.cpd.exclusions",
            """
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/JsonApiDocumentReader.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/JsonApiDomainDocument.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/JsonApiJackson2Assembly.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/JsonApiPatchCommandReader.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/JsonApiPatchDtoReader.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/Jackson2JsonApi.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/Jackson2JsonApiResources.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/BeanConstruction.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/Jackson2InclusionBackend.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/codec/DocumentWireReader.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/DomainPatchBinder.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/DomainPatchDtoBinder.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/DomainResourceBinder.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/DomainResourceWriter.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/codec/ErrorWireReader.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/FlatConstructionPaths.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/codec/LinkWireReader.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/MappingDefinitionCache.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/MappingDefinitionResolver.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/MappingTypeSupport.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/MetaBindingModule.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/PatchMemberConverter.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/PatchPresenceDeserializer.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/PropertyScopedValueConverter.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/RawValueBeanPropertyWriter.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/RelationshipLinkageSupport.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/codec/ResourceWireReader.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/ResolvedTypeSupport.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/StructuredValueBinder.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/codec/WireTokens.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/WholeMetaTarget.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/WrapperCustomization.java
            """.trimIndent(),
        )
    }
}

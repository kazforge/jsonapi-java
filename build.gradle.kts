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
        // adapter-local adaptations of their Jackson 3 counterparts (document codec, read-side,
        // typed domain envelope, write-side domain mapping engine, flat DTO resource binding, and
        // presence-aware PATCH binding), kept adapter-local for Jackson-major isolation per ADR-007.
        // Jackson-major-neutral implementation bookkeeping is shared in jackson-api; do not exclude
        // whole packages.
        property(
            "sonar.cpd.exclusions",
            """
            src/main/java/com/kazforge/jsonapi/jackson2/JsonApiDocumentReader.java,
            src/main/java/com/kazforge/jsonapi/jackson2/JsonApiDomainDocument.java,
            src/main/java/com/kazforge/jsonapi/jackson2/JsonApiDomainDocumentReader.java,
            src/main/java/com/kazforge/jsonapi/jackson2/JsonApiPatchCommandReader.java,
            src/main/java/com/kazforge/jsonapi/jackson2/JsonApiPatchDtoReader.java,
            src/main/java/com/kazforge/jsonapi/jackson2/JsonApiResourceBinder.java,
            src/main/java/com/kazforge/jsonapi/jackson2/JsonApiResourceMapper.java,
            src/main/java/com/kazforge/jsonapi/jackson2/Jackson2JsonApi.java,
            src/main/java/com/kazforge/jsonapi/jackson2/Jackson2JsonApiDocuments.java,
            src/main/java/com/kazforge/jsonapi/jackson2/Jackson2JsonApiPatches.java,
            src/main/java/com/kazforge/jsonapi/jackson2/Jackson2JsonApiRelationships.java,
            src/main/java/com/kazforge/jsonapi/jackson2/Jackson2JsonApiResources.java,
            src/main/java/com/kazforge/jsonapi/jackson2/RelationshipLinkageMapper.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/BeanConstruction.java,
            src/main/java/com/kazforge/jsonapi/jackson2/BinderMetaConverter.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/CompoundInclusionEngine.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/DocumentWireReader.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/DomainPatchBinder.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/DomainPatchDtoBinder.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/DomainResourceBinder.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/DomainResourceWriter.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/ErrorWireReader.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/FlatConstructionPaths.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/JsonApiWireReader.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/LinkWireReader.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/MappingDefinitionCache.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/MappingDefinitionResolver.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/MetaBindingModule.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/PatchMemberConverter.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/PatchPresenceDeserializer.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/PatchPresenceModule.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/PresenceMarkerSerializer.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/PropertyScopedValueConverter.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/RawValueBeanPropertyWriter.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/RawValuePropertyModule.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/ReadLocations.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/ReadMappingProperty.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/ReadResourceMapping.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/RelationshipLinkageSupport.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/ResourceWireReader.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/ResolvedTypeSupport.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/StructuredValueBinder.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/WireObjectMembers.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/WireTokens.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/WholeMetaTarget.java,
            src/main/java/com/kazforge/jsonapi/jackson2/internal/WrapperCustomization.java
            """.trimIndent(),
        )
    }
}

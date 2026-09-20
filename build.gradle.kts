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
        // Paths must be repository-root-relative; module-relative `src/main/java/...` at project
        // level is deprecated by SonarCloud.
        property(
            "sonar.cpd.exclusions",
            """
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/JsonApiDocumentReader.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/JsonApiDomainDocument.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/JsonApiDomainDocumentReader.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/JsonApiJackson2Assembly.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/JsonApiPatchCommandReader.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/JsonApiPatchDtoReader.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/JsonApiResourceBinder.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/JsonApiResourceMapper.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/Jackson2JsonApi.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/Jackson2JsonApiDocuments.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/Jackson2JsonApiPatches.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/Jackson2JsonApiRelationships.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/Jackson2JsonApiResources.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/mapping/RelationshipLinkageMapper.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/BeanConstruction.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/BinderMetaConverter.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/CompoundInclusionEngine.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/codec/DocumentWireReader.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/DomainPatchBinder.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/DomainPatchDtoBinder.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/DomainResourceBinder.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/DomainResourceWriter.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/codec/ErrorWireReader.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/FlatConstructionPaths.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/codec/JsonApiWireReader.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/codec/LinkWireReader.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/MappingConstructionStart.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/MappingDefinitionCache.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/MappingDefinitionResolver.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/MappingTypeSupport.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/MetaBindingModule.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/PatchMemberConverter.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/PatchPresenceDeserializer.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/PatchPresenceModule.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/PresenceMarkerSerializer.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/PropertyScopedValueConverter.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/RawValueBeanPropertyWriter.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/RawValuePropertyModule.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/codec/ReadLocations.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/ReadMappingProperty.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/ReadResourceMapping.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/RelationshipLinkageSupport.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/codec/ResourceWireReader.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/ResolvedTypeSupport.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/StructuredValueBinder.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/codec/WireMetaReader.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/codec/WireObjectMembers.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/codec/WireOpenValues.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/codec/WireTokens.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/WholeMetaTarget.java,
            jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/internal/WrapperCustomization.java
            """.trimIndent(),
        )
    }
}

// KAZ-138 temporary CI helper: capture Spotless' exact Java output for the PoC branch.
// Remove after the formatted sources have been committed.
val collectSpotlessFormattedSources =
    tasks.register<Copy>("collectSpotlessFormattedSources") {
        dependsOn("spotlessApply")
        from(layout.projectDirectory) {
            include("**/*.java")
            exclude("**/build/**", "**/bin/**")
        }
        into(layout.buildDirectory.dir("reports/jacoco/spotless-formatted"))
    }

tasks.named("spotlessCheck") {
    dependsOn("spotlessApply")
    finalizedBy(collectSpotlessFormattedSources)
}

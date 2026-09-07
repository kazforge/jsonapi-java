package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import java.util.List;
import org.jspecify.annotations.Nullable;

record ResourceMapping(
    String resourceType,
    @Nullable MappingProperty identifierProperty,
    @Nullable MappingProperty localIdProperty,
    List<MappingProperty> attributes,
    List<MappingProperty> relationships,
    @Nullable MappingProperty resourceMeta,
    List<MappingProperty> relationshipMetaProperties,
    JavaType domainType) {}

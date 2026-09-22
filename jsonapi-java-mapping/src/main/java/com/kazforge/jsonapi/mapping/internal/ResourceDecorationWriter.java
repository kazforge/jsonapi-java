package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.mapping.RelationshipDecoration;
import com.kazforge.jsonapi.mapping.ResourceDecoration;
import com.kazforge.jsonapi.mapping.ResourceDecorator;
import com.kazforge.jsonapi.mapping.ResourceDecoratorRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral additive link-decoration phase for domain writes.
 *
 * <p>It owns the decoration orchestration both Jackson adapters previously duplicated: exact lookup
 * of one {@link ResourceDecorator} by the already-resolved effective runtime raw class, decorator
 * failure/null translation, relationship target classification against the neutral write
 * definition, logical-name to JSON:API-name resolution, whole-value resource and relationship link
 * replacement, fieldset non-resurrection, and reconstruction that preserves every other member the
 * basic write produced. A non-null decoration {@link Links} value replaces the complete existing
 * resource or relationship links; members are never merged. Decoration never creates a relationship
 * that ordinary mapping did not emit.
 *
 * <p>The effective-type resolution that yields {@code effectiveRawClass} stays at the adapter edge:
 * resolving it is a configured backend type operation with its own failure timing, and the adapter
 * returns before resolution when its registry is empty. The decorator contract values and registry
 * remain neutral API. This is unsupported implementation detail for backend cooperation, not
 * consumer SPI.
 */
@NullMarked
public final class ResourceDecorationWriter {

  private ResourceDecorationWriter() {}

  /**
   * Applies additive links from the exact-match decorator for {@code effectiveRawClass} to one
   * already basic-written resource, returning the resource unchanged when no decorator matches or
   * the decoration contributes no links.
   *
   * @param base the resource produced by {@link BasicResourceWriter#writeBasic}
   * @param domain the application domain object being written
   * @param effectiveRawClass the already-resolved effective runtime raw class used for exact lookup
   * @param definition the neutral write definition for the resource
   * @param allowedFields the selected JSON:API field names, or {@code null} when not selective
   * @param decoratorRegistry the configured decorator registry
   */
  @SuppressWarnings("NullAway")
  public static ResourceObject decorate(
      ResourceObject base,
      Object domain,
      Class<?> effectiveRawClass,
      WriteResourceDefinition<?> definition,
      @Nullable Set<String> allowedFields,
      ResourceDecoratorRegistry decoratorRegistry) {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(domain, "domain");
    Objects.requireNonNull(effectiveRawClass, "effectiveRawClass");
    Objects.requireNonNull(definition, "definition");
    Objects.requireNonNull(decoratorRegistry, "decoratorRegistry");
    @SuppressWarnings("unchecked")
    ResourceDecorator<Object> decorator =
        (ResourceDecorator<Object>) decoratorRegistry.decoratorFor(effectiveRawClass);
    if (decorator == null) {
      return base;
    }
    String resourceType = definition.resourceType();
    ResourceDecoration decoration = requireDecoration(domain, decorator, resourceType);
    Map<String, RelationshipDecoration> decorationRelationships =
        requireDecorationRelationships(domain, decoration, resourceType);
    LinkedHashMap<String, Relationship> decoratedRelationships =
        resolveRelationshipDecorations(
            domain, definition, base, decorationRelationships, allowedFields);
    Links resourceLinks = decoration.links();
    boolean hasResourceLinks = resourceLinks != null;
    boolean hasRelationshipLinks = decoratedRelationships != null;
    if (!hasResourceLinks && !hasRelationshipLinks) {
      return base;
    }
    Relationships finalRelationships;
    if (hasRelationshipLinks) {
      finalRelationships = Relationships.ofRelationships(decoratedRelationships);
    } else if (base.relationships() == null) {
      finalRelationships = Relationships.empty();
    } else {
      finalRelationships = base.relationships();
    }
    return new ResourceObject(
        base.type(),
        base.id(),
        base.lid(),
        base.attributes(),
        finalRelationships.isEmpty() ? null : finalRelationships,
        hasResourceLinks ? resourceLinks : base.links(),
        base.meta(),
        base.additionalMembers());
  }

  @SuppressWarnings({"java:S2583", "ConstantValue"})
  private static ResourceDecoration requireDecoration(
      Object domain, ResourceDecorator<Object> decorator, String resourceType) {
    ResourceDecoration decoration;
    try {
      decoration = decorator.decorate(domain);
    } catch (RuntimeException e) {
      throw new JsonApiMappingException(
          MappingDiagnostic.INVALID_DECORATION_STATE,
          domain.getClass(),
          null,
          "Decorator failed for " + resourceType + ": " + e.getMessage(),
          e);
    }
    if (decoration == null) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.INVALID_DECORATION_STATE,
          domain.getClass(),
          "Decorator returned null for " + resourceType);
    }
    return decoration;
  }

  @SuppressWarnings("java:S2583")
  private static Map<String, RelationshipDecoration> requireDecorationRelationships(
      Object domain, ResourceDecoration decoration, String resourceType) {
    Map<String, RelationshipDecoration> decorationRelationships;
    try {
      decorationRelationships = decoration.relationships();
    } catch (RuntimeException e) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.INVALID_DECORATION_STATE,
          domain.getClass(),
          "Invalid decoration relationships for " + resourceType);
    }
    // ResourceDecoration is final with a validated private constructor, so relationships() cannot
    // return null; no defensive null branch is needed here.
    return decorationRelationships;
  }

  @SuppressWarnings("NullAway")
  private static @Nullable LinkedHashMap<String, Relationship> resolveRelationshipDecorations(
      Object domain,
      WriteResourceDefinition<?> definition,
      ResourceObject base,
      Map<String, RelationshipDecoration> decorationRelationships,
      @Nullable Set<String> allowedFields) {
    if (decorationRelationships.isEmpty()) {
      return null;
    }
    Map<String, String> wireNames = new LinkedHashMap<>();
    for (WriteProperty<?> property : definition.relationships()) {
      wireNames.put(property.logicalName(), property.jsonapiName());
    }
    Map<String, String> nonRelationshipKind = indexNonRelationships(definition);
    Map<String, Relationship> baseRelationships =
        base.relationships() == null ? Map.of() : base.relationships().relationships();
    LinkedHashMap<String, Relationship> decoratedRelationships = null;
    for (Map.Entry<String, RelationshipDecoration> entry : decorationRelationships.entrySet()) {
      String logicalName = entry.getKey();
      RelationshipDecoration relationshipDecoration = entry.getValue();
      validateDecorationEntry(domain, logicalName, relationshipDecoration);
      String wireName = wireNames.get(logicalName);
      if (wireName == null) {
        throwInvalidTarget(domain, definition.resourceType(), logicalName, nonRelationshipKind);
        continue;
      }
      Relationship existing = baseRelationships.get(wireName);
      Links decorationLinks = relationshipDecoration.links();
      boolean shouldDecorate =
          (allowedFields == null || allowedFields.contains(wireName))
              && existing != null
              && decorationLinks != null;
      if (shouldDecorate) {
        if (decoratedRelationships == null) {
          decoratedRelationships = new LinkedHashMap<>(baseRelationships);
        }
        Relationship nonNullExisting = Objects.requireNonNull(existing, "existing");
        decoratedRelationships.put(
            wireName,
            new Relationship(
                nonNullExisting.data(),
                decorationLinks,
                nonNullExisting.meta(),
                nonNullExisting.additionalMembers()));
      }
    }
    return decoratedRelationships;
  }

  private static Map<String, String> indexNonRelationships(WriteResourceDefinition<?> definition) {
    Map<String, String> nonRelationshipKind = new LinkedHashMap<>();
    WriteProperty<?> identifierProperty = definition.identifier();
    if (identifierProperty != null) {
      nonRelationshipKind.put(identifierProperty.logicalName(), "identifier");
    }
    WriteProperty<?> localIdProperty = definition.localId();
    if (localIdProperty != null) {
      nonRelationshipKind.put(localIdProperty.logicalName(), "identifier");
    }
    for (WriteProperty<?> property : definition.attributes()) {
      nonRelationshipKind.put(property.logicalName(), "attribute");
    }
    WriteProperty<?> resourceMeta = definition.resourceMeta();
    if (resourceMeta != null) {
      nonRelationshipKind.put(resourceMeta.logicalName(), "resource meta");
    }
    for (WriteProperty<?> property : definition.relationshipMeta()) {
      nonRelationshipKind.put(property.logicalName(), "relationship meta");
    }
    return nonRelationshipKind;
  }

  @SuppressWarnings("java:S2583")
  private static void validateDecorationEntry(
      Object domain,
      @Nullable String logicalName,
      @Nullable RelationshipDecoration relationshipDecoration) {
    if (logicalName == null) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.INVALID_DECORATION_STATE,
          domain.getClass(),
          "Decoration contains null relationship property");
    }
    if (logicalName.isEmpty()) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.INVALID_DECORATION_STATE,
          domain.getClass(),
          "Decoration contains empty relationship property");
    }
    if (relationshipDecoration == null) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.INVALID_DECORATION_STATE,
          domain.getClass(),
          "Decoration for relationship '" + logicalName + "' is null");
    }
  }

  private static void throwInvalidTarget(
      Object domain,
      String resourceType,
      String logicalName,
      Map<String, String> nonRelationshipKind) {
    String kind = nonRelationshipKind.get(logicalName);
    if (kind != null) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.INVALID_DECORATION_TARGET,
          domain.getClass(),
          "Decoration target '"
              + logicalName
              + "' is a "
              + kind
              + ", not a relationship on "
              + resourceType);
    }
    throw JsonApiMappingException.withoutLocation(
        MappingDiagnostic.INVALID_DECORATION_TARGET,
        domain.getClass(),
        "Unknown decoration target '" + logicalName + "' on " + resourceType);
  }
}

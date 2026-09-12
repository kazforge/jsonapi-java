package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.core.model.Attributes;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.jackson.diagnostic.MappingLocation;
import com.kazforge.jsonapi.jackson.internal.mapping.ResourceTypeMatch;
import com.kazforge.jsonapi.jackson.internal.patch.PresenceMarker;
import com.kazforge.jsonapi.jackson.mapping.IdentifierConverter;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;
import com.kazforge.jsonapi.jackson3.RelationshipLinkageMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Binds a validated single-resource update directly into an application-owned annotated PATCH DTO.
 *
 * <p>Every resolver-classified attribute and relationship member of the PATCH DTO must be declared
 * exactly as {@code PatchPresence<T>} without wrapper-level {@code @JsonDeserialize} /
 * {@code @JsonSerialize}; violations fail with {@link
 * MappingDiagnostic#INVALID_PATCH_PROPERTY_TYPE} at the member's resource-relative wire location
 * before any member binds. The identifier property is a normal identifier (never a patchable
 * member). Supplied atomic members retain their JSON-compatible wire values in a synthetic property
 * map as an internal {@link PresenceMarker}; the marker deserializer performs the sole inner-type
 * conversion while the bean is constructed with a single {@link JsonMapper#convertValue(Object,
 * JavaType)}. Creators, deserializers, converters, and configured modules therefore remain
 * authoritative (ADR-004). The JSON:API identifier is parsed first and then follows the same
 * target-property deserialization at construction time. Omitted members bind to {@code
 * PatchPresence.omitted()}; supplied unknown members fail with {@link
 * MappingDiagnostic#UNKNOWN_PATCH_MEMBER} at their escaped supplied wire name. Document {@code
 * included} is never read.
 *
 * <p>Recursive structured attributes (ADR-014) use the {@link StructuredValueBinder}: a nested
 * member whose inner type is a deliberately presence-aware PATCH shape is bound as a complete
 * nested {@link PresenceMarker} tree so the single whole-tree {@code convertValue} preserves the
 * strict marker invariant. Deep Jackson construction-failure paths are translated to wire-name
 * locations through the resolved shape metadata.
 */
public final class DomainPatchDtoBinder {

  private static final String ATTRIBUTES = "attributes";
  private static final String RELATIONSHIPS = "relationships";
  private static final MappingLocation ID_LOCATION = MappingLocation.of("id");
  private static final MappingLocation RELATIONSHIPS_LOCATION = MappingLocation.of(RELATIONSHIPS);

  private final JsonMapper mapper;
  private final MappingDefinitionCache cache;
  private final PatchMemberConverter converter;
  private final StructuredValueBinder structuredBinder;
  private final WholeMetaTarget wholeMetaTarget;

  public DomainPatchDtoBinder(
      JsonMapper mapper,
      IdentifierConverter identifierConverter,
      MappingDefinitionCache cache,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.cache = Objects.requireNonNull(cache, "cache");
    this.converter = new PatchMemberConverter(mapper, identifierConverter, linkageMappers);
    this.structuredBinder = new StructuredValueBinder(mapper);
    this.wholeMetaTarget = new WholeMetaTarget(mapper);
  }

  /** Binds one resource object into a PATCH DTO instance of {@code targetType}. */
  public Object fromResource(ResourceObject resource, JavaType targetType) {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(targetType, "targetType");
    Class<?> rawType = targetType.getRawClass();
    ResourceMapping mapping = cache.resolve(targetType);
    validateResourceType(resource, mapping, rawType);
    validatePatchDtoDeclaration(mapping, rawType);
    Map<String, @Nullable Object> properties = new LinkedHashMap<>();
    bindIdentity(resource, mapping, properties, rawType);
    bindAttributes(resource, mapping, properties, rawType);
    bindRelationships(resource, mapping, properties, rawType);
    bindResourceMeta(resource, mapping, properties, rawType);
    bindRelationshipMeta(resource, mapping, properties, rawType);
    Map<String, StructuredValueBinder.ConstructionStart> startsByJacksonName =
        mapping.constructionStartsByJacksonName(ID_LOCATION, null);
    try {
      return BeanConstruction.convertBean(
          mapper,
          properties,
          targetType,
          rawType,
          (failure, ignored) ->
              structuredBinder.translateConstructionPath(
                  BeanConstruction.pathNames(failure), startsByJacksonName, false));
    } catch (JsonApiMappingException e) {
      if (isIdentifierConstructionFailure(e, mapping.identifierProperty())) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        throw new JsonApiMappingException(
            MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED,
            rawType,
            ID_LOCATION,
            "Failed to convert the wire identifier at '"
                + ID_LOCATION
                + "' for "
                + rawType.getName(),
            cause);
      }
      throw e;
    }
  }

  /** Resource-relative wire location of one top-level PATCH DTO attribute. */
  private static MappingLocation attributeLocation(MappingProperty property) {
    return MappingLocation.of(ATTRIBUTES, property.jsonapiName());
  }

  /** Resource-relative wire location of one top-level relationship's linkage. */
  private static MappingLocation relationshipLocation(MappingProperty property) {
    return MappingLocation.of(RELATIONSHIPS, property.jsonapiName(), "data");
  }

  private void validateResourceType(
      ResourceObject resource, ResourceMapping mapping, Class<?> rawType) {
    ResourceTypeMatch.requireMatching(mapping.resourceType(), resource, rawType);
  }

  /**
   * PATCH DTO declaration check: every mapped attribute and relationship member must be exactly
   * {@code PatchPresence<T>} and must not carry wrapper-level Jackson customization. Unannotated
   * ordinary properties do not participate and are not declaration-checked.
   */
  private void validatePatchDtoDeclaration(ResourceMapping mapping, Class<?> rawType) {
    MappingProperty identifier = mapping.identifierProperty();
    if (identifier != null && isPatchPresenceType(identifier.accessor().getType())) {
      throw new JsonApiMappingException(
          MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE,
          rawType,
          ID_LOCATION,
          "PATCH DTO identifier '"
              + identifier.logicalName()
              + "' must not be declared as PatchPresence on "
              + rawType.getName());
    }
    for (MappingProperty property : mapping.attributes()) {
      validatePatchableProperty(property, rawType, attributeLocation(property));
    }
    for (MappingProperty property : mapping.relationships()) {
      validatePatchableProperty(property, rawType, relationshipLocation(property));
    }
    MappingProperty resourceMeta = mapping.resourceMeta();
    if (resourceMeta != null) {
      validatePatchableMetaProperty(
          resourceMeta, rawType, RelationshipMetaSupport.resourceMetaLocation());
    }
    for (MappingProperty property : mapping.relationshipMetaProperties()) {
      validatePatchableMetaProperty(
          property,
          rawType,
          RelationshipMetaSupport.relationshipMetaLocation(property.jsonapiName()));
    }
    wholeMetaTarget.validateRelationshipLinkageMeta(mapping.relationships(), rawType);
  }

  private void validatePatchableProperty(
      MappingProperty property, Class<?> rawType, MappingLocation memberLocation) {
    JavaType type = property.definition().getPrimaryType();
    if (!isPatchPresenceType(type)
        || WrapperCustomization.has(
            mapper, type, property.accessor(), property.definition().getMutator())) {
      throw new JsonApiMappingException(
          MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE,
          rawType,
          memberLocation,
          "PATCH DTO member '"
              + property.logicalName()
              + "' must be declared exactly as PatchPresence<T> without wrapper-level "
              + "@JsonDeserialize/@JsonSerialize customization on "
              + rawType.getName());
    }
  }

  /**
   * Whole-meta member validation for the typed PATCH DTO role (ADR-015): the shared patchable
   * member authority (exactly {@code PatchPresence<T>}, no wrapper-level customization) plus, after
   * unwrapping one {@code PatchPresence} and at most one {@link java.util.Optional}, an effective
   * Bean / Map / Object target.
   */
  private void validatePatchableMetaProperty(
      MappingProperty property, Class<?> rawType, MappingLocation memberLocation) {
    validatePatchableProperty(property, rawType, memberLocation);
    if (!wholeMetaTarget.validTypedPatchTarget(property.definition().getPrimaryType())) {
      throw new JsonApiMappingException(
          MappingDiagnostic.INVALID_META_TARGET,
          rawType,
          memberLocation,
          "PATCH DTO meta member '"
              + property.logicalName()
              + "' must be PatchPresence<Bean|Map|Object> (with at most one Optional inside) on "
              + rawType.getName());
    }
  }

  private static boolean isPatchPresenceType(JavaType type) {
    return type.getRawClass() == PatchPresence.class && type.containedTypeCount() == 1;
  }

  private void bindIdentity(
      ResourceObject resource,
      ResourceMapping mapping,
      Map<String, @Nullable Object> properties,
      Class<?> rawType) {
    MappingProperty identifierProperty = mapping.identifierProperty();
    if (identifierProperty == null || !resource.hasId()) {
      throw new JsonApiMappingException(
          MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED,
          rawType,
          ID_LOCATION,
          "Resource update identity requires a non-null id at '" + ID_LOCATION + "'");
    }
    Object identity = converter.parseIdentity(Objects.requireNonNull(resource.id()), rawType);
    properties.put(identifierProperty.jacksonName(), identity);
  }

  private static boolean isIdentifierConstructionFailure(
      JsonApiMappingException failure, @Nullable MappingProperty identifierProperty) {
    if (identifierProperty == null) {
      return false;
    }
    return BeanConstruction.isConstructionFailureForProperty(
        failure, identifierProperty, ID_LOCATION);
  }

  private void bindAttributes(
      ResourceObject resource,
      ResourceMapping mapping,
      Map<String, @Nullable Object> properties,
      Class<?> rawType) {
    Attributes attributes = resource.attributes();
    Map<String, @Nullable Object> supplied = attributes == null ? null : attributes.attributes();
    Map<String, MappingProperty> byJsonapiName =
        PatchMemberConverter.byJsonapiName(mapping.attributes());
    MappingLocation attributesLocation = MappingLocation.of(ATTRIBUTES);
    if (supplied != null) {
      for (String name : supplied.keySet()) {
        if (!byJsonapiName.containsKey(name)) {
          throw unknownPatchMember(rawType, attributesLocation.append(name), "attribute", name);
        }
      }
    }
    for (MappingProperty property : mapping.attributes()) {
      if (supplied != null && supplied.containsKey(property.jsonapiName())) {
        Object value =
            structuredBinder.typedMemberValue(
                supplied.get(property.jsonapiName()),
                property.accessor().getType(),
                attributeLocation(property),
                rawType);
        properties.put(property.jacksonName(), new PresenceMarker(true, value));
      } else {
        properties.put(property.jacksonName(), new PresenceMarker(false, null));
      }
    }
  }

  private void bindRelationships(
      ResourceObject resource,
      ResourceMapping mapping,
      Map<String, @Nullable Object> properties,
      Class<?> rawType) {
    Relationships relationships = resource.relationships();
    Map<String, Relationship> supplied =
        relationships == null ? null : relationships.relationships();
    Map<String, MappingProperty> byJsonapiName =
        PatchMemberConverter.byJsonapiName(mapping.relationships());
    if (supplied != null) {
      for (String name : supplied.keySet()) {
        if (!byJsonapiName.containsKey(name)) {
          throw unknownPatchMember(
              rawType, RELATIONSHIPS_LOCATION.append(name), "relationship", name);
        }
      }
    }
    for (MappingProperty property : mapping.relationships()) {
      Relationship relationship = supplied == null ? null : supplied.get(property.jsonapiName());
      RelationshipData data = relationship == null ? null : relationship.data();
      if (data == null) {
        // Omitted, or a supplied mapped relationship lacking data (only reachable on the
        // non-revalidating fromDocument path): bind as Omitted, mirroring the low-level skip.
        properties.put(property.jacksonName(), new PresenceMarker(false, null));
        continue;
      }
      Object value = converter.convertRelationshipForPatchDto(property, data, innerType(property));
      properties.put(property.jacksonName(), new PresenceMarker(true, value));
    }
  }

  private static JsonApiMappingException unknownPatchMember(
      Class<?> rawType, MappingLocation path, String kind, String name) {
    return new JsonApiMappingException(
        MappingDiagnostic.UNKNOWN_PATCH_MEMBER,
        rawType,
        path,
        "Unknown supplied " + kind + " '" + name + "' for PATCH DTO " + rawType.getName());
  }

  /**
   * Binds supplied resource meta as a {@code PatchPresence} member. Supplied meta without a
   * declared {@code @JsonApiMeta} member is rejected on the strict typed path (ADR-015).
   */
  private void bindResourceMeta(
      ResourceObject resource,
      ResourceMapping mapping,
      Map<String, @Nullable Object> properties,
      Class<?> rawType) {
    MappingProperty property = mapping.resourceMeta();
    if (property == null) {
      if (resource.meta() != null) {
        throw unknownPatchMember(
            rawType, RelationshipMetaSupport.resourceMetaLocation(), "meta", "meta");
      }
      return;
    }
    if (resource.meta() == null) {
      properties.put(property.jacksonName(), new PresenceMarker(false, null));
      return;
    }
    Object value =
        structuredBinder.typedMemberValue(
            resource.meta().members(),
            property.accessor().getType(),
            RelationshipMetaSupport.resourceMetaLocation(),
            rawType);
    properties.put(property.jacksonName(), new PresenceMarker(true, value));
  }

  /**
   * Binds supplied relationship meta for each mapped relationship-meta member. Meta participates
   * only when the relationship carries {@code data}; supplied meta for a mapped relationship
   * without a declared {@code @JsonApiRelationshipMeta} member is rejected on the strict typed path
   * (ADR-015).
   */
  private void bindRelationshipMeta(
      ResourceObject resource,
      ResourceMapping mapping,
      Map<String, @Nullable Object> properties,
      Class<?> rawType) {
    Relationships relationships = resource.relationships();
    Map<String, Relationship> supplied =
        relationships == null ? null : relationships.relationships();
    Map<String, MappingProperty> metaByTarget =
        RelationshipMetaSupport.byTarget(mapping.relationshipMetaProperties());
    if (supplied != null) {
      for (Map.Entry<String, Relationship> entry : supplied.entrySet()) {
        Relationship relationship = entry.getValue();
        if (relationship.data() != null
            && relationship.meta() != null
            && !metaByTarget.containsKey(entry.getKey())) {
          throw unknownPatchMember(
              rawType,
              RelationshipMetaSupport.relationshipMetaLocation(entry.getKey()),
              "relationship meta",
              entry.getKey());
        }
      }
    }
    for (MappingProperty property : mapping.relationshipMetaProperties()) {
      Relationship relationship = supplied == null ? null : supplied.get(property.jsonapiName());
      if (relationship == null || relationship.data() == null || relationship.meta() == null) {
        properties.put(property.jacksonName(), new PresenceMarker(false, null));
        continue;
      }
      MappingLocation location =
          RelationshipMetaSupport.relationshipMetaLocation(property.jsonapiName());
      Object value =
          structuredBinder.typedMemberValue(
              relationship.meta().members(), property.accessor().getType(), location, rawType);
      properties.put(property.jacksonName(), new PresenceMarker(true, value));
    }
  }

  private static JavaType innerType(MappingProperty property) {
    return property.accessor().getType().containedType(0);
  }
}

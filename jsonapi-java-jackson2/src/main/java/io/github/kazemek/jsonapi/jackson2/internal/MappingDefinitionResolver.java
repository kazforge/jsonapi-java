package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.github.kazemek.jsonapi.annotation.JsonApiAttribute;
import io.github.kazemek.jsonapi.annotation.JsonApiId;
import io.github.kazemek.jsonapi.annotation.JsonApiLocalId;
import io.github.kazemek.jsonapi.annotation.JsonApiMeta;
import io.github.kazemek.jsonapi.annotation.JsonApiRelationship;
import io.github.kazemek.jsonapi.annotation.JsonApiRelationshipMeta;
import io.github.kazemek.jsonapi.annotation.JsonApiResource;
import io.github.kazemek.jsonapi.core.model.JsonApiMembers;
import io.github.kazemek.jsonapi.core.validation.MemberNames;
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingLocation;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class MappingDefinitionResolver {

  private MappingDefinitionResolver() {}

  static ResourceMapping resolve(
      BeanDescription beanDescription, Class<?> rawType, AnnotatedClass resourceMetadata) {
    String resourceType = validateResourceTypeName(resourceTypeName(resourceMetadata), rawType);

    List<BeanPropertyDefinition> propertyDefinitions = beanDescription.findProperties();
    ClassifiedProperties classified = classifyProperties(propertyDefinitions, rawType);
    validatePropertyRoles(
        classified.identifiers,
        classified.localIds,
        classified.attributes,
        classified.relationships,
        classified.resourceMeta,
        classified.relationshipMeta,
        rawType);

    MappingProperty identifier =
        classified.identifiers.isEmpty() ? null : classified.identifiers.getFirst();
    MappingProperty localId = classified.localIds.isEmpty() ? null : classified.localIds.getFirst();
    MappingProperty resourceMeta =
        classified.resourceMeta.isEmpty() ? null : classified.resourceMeta.getFirst();
    return new ResourceMapping(
        resourceType,
        identifier,
        localId,
        List.copyOf(classified.attributes),
        List.copyOf(classified.relationships),
        resourceMeta,
        List.copyOf(classified.relationshipMeta),
        beanDescription.getType());
  }

  /**
   * Reads the configured class-level resource type name from the mapper-introspected direct class
   * annotations, or {@code null} when absent. This is the single interpretation of class-level
   * {@link JsonApiResource} metadata: the direct {@link AnnotatedClass} carries the configured
   * mapper's view of the target class, including a target-specific class-level mix-in, without
   * importing annotations from supertypes or interfaces.
   */
  static @Nullable String resourceTypeName(AnnotatedClass annotatedClass) {
    // Jackson's getAnnotation is not @Nullable-annotated; use hasAnnotation as the presence check.
    if (!annotatedClass.hasAnnotation(JsonApiResource.class)) {
      return null;
    }
    return annotatedClass.getAnnotation(JsonApiResource.class).type();
  }

  /**
   * Validates a class-level resource type name resolved by {@link #resourceTypeName}.
   *
   * @throws JsonApiMappingException {@link MappingDiagnostic#MISSING_RESOURCE_ANNOTATION} when the
   *     name is absent, or {@link MappingDiagnostic#INVALID_RESOURCE_TYPE} when it is empty or not
   *     a valid JSON:API member name
   */
  static String validateResourceTypeName(@Nullable String resourceTypeName, Class<?> rawType) {
    if (resourceTypeName == null) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.MISSING_RESOURCE_ANNOTATION,
          rawType,
          "Missing @JsonApiResource on " + rawType.getName());
    }
    if (resourceTypeName.isEmpty()) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.INVALID_RESOURCE_TYPE,
          rawType,
          "@JsonApiResource.type() must not be empty on " + rawType.getName());
    }
    if (!MemberNames.isValid(resourceTypeName)) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.INVALID_RESOURCE_TYPE,
          rawType,
          "Invalid resource type name: " + resourceTypeName);
    }
    return resourceTypeName;
  }

  private static ClassifiedProperties classifyProperties(
      List<BeanPropertyDefinition> propertyDefinitions, Class<?> rawType) {
    ClassifiedProperties classified = new ClassifiedProperties();
    for (BeanPropertyDefinition propertyDefinition : propertyDefinitions) {
      MappingProperty mappingProperty = classifyProperty(propertyDefinition, rawType);
      if (mappingProperty != null) {
        classified.add(mappingProperty);
      }
    }
    List<MappingProperty> boundMeta =
        bindWriteRelationshipMeta(classified.relationshipMeta, classified.relationships, rawType);
    classified.relationshipMeta.clear();
    classified.relationshipMeta.addAll(boundMeta);
    return classified;
  }

  /**
   * Classifies one Jackson property into a write mapping member, or {@code null} when it does not
   * participate. Role and wire name resolve from annotations plus configured Jackson so
   * member-level declaration failures can report the member's resource-relative wire location even
   * when the accessor is missing. Unannotated properties do not participate. Merged Jackson names
   * are rejected before that skip so role-bearing collisions cannot disappear.
   */
  private static @Nullable MappingProperty classifyProperty(
      BeanPropertyDefinition propertyDefinition, Class<?> rawType) {
    RoleAnnotations annotations = RoleAnnotations.from(propertyDefinition);
    String jacksonName = propertyDefinition.getName();
    String logicalName = propertyDefinition.getInternalName();
    rejectConflictingJacksonName(annotations, jacksonName, rawType);
    PropertyRole role = resolveRole(annotations, jacksonName, logicalName, rawType);
    if (role == null) {
      return null;
    }
    String jsonapiName = resolveJsonapiName(annotations, jacksonName, role);
    validateJsonApiName(jsonapiName, role, logicalName, rawType);
    AnnotatedMember accessor =
        requireAccessorIfAnnotated(
            propertyDefinition, logicalName, rawType, annotations, wireLocation(role, jsonapiName));
    if (accessor == null) {
      return null;
    }
    return new MappingProperty(propertyDefinition, accessor, logicalName, jsonapiName, role);
  }

  private static final class ClassifiedProperties {
    private final List<MappingProperty> identifiers = new ArrayList<>();
    private final List<MappingProperty> localIds = new ArrayList<>();
    private final List<MappingProperty> attributes = new ArrayList<>();
    private final List<MappingProperty> relationships = new ArrayList<>();
    private final List<MappingProperty> resourceMeta = new ArrayList<>();
    private final List<MappingProperty> relationshipMeta = new ArrayList<>();

    private void add(MappingProperty mappingProperty) {
      switch (mappingProperty.role()) {
        case ID -> identifiers.add(mappingProperty);
        case LOCAL_ID -> localIds.add(mappingProperty);
        case ATTRIBUTE -> attributes.add(mappingProperty);
        case RELATIONSHIP -> relationships.add(mappingProperty);
        case RESOURCE_META -> resourceMeta.add(mappingProperty);
        case RELATIONSHIP_META -> relationshipMeta.add(mappingProperty);
      }
    }
  }

  /**
   * Resource-relative wire location of one classified member: {@code /id}, {@code /lid}, {@code
   * /attributes/<name>}, {@code /relationships/<name>/data}, {@code /meta}, or {@code
   * /relationships/<name>/meta}. The name is escaped as pointer segments per RFC 6901.
   */
  private static MappingLocation wireLocation(PropertyRole role, String jsonapiName) {
    return switch (role) {
      case ID -> MappingLocation.of("id");
      case LOCAL_ID -> MappingLocation.of("lid");
      case ATTRIBUTE -> attributeLocation(jsonapiName);
      case RELATIONSHIP -> relationshipLocation(jsonapiName);
      case RESOURCE_META -> RelationshipMetaSupport.resourceMetaLocation();
      case RELATIONSHIP_META -> RelationshipMetaSupport.relationshipMetaLocation(jsonapiName);
    };
  }

  private static @Nullable AnnotatedMember requireAccessorIfAnnotated(
      BeanPropertyDefinition propertyDefinition,
      String logicalName,
      Class<?> rawType,
      RoleAnnotations annotations,
      @Nullable MappingLocation memberLocation) {
    AnnotatedMember accessor = propertyDefinition.getAccessor();
    if (accessor != null) {
      return accessor;
    }
    if (annotations.hasAny()) {
      throw new JsonApiMappingException(
          MappingDiagnostic.MISSING_ACCESSOR,
          rawType,
          memberLocation,
          "Annotated property '" + logicalName + "' has no readable accessor");
    }
    return null;
  }

  /**
   * Configured Jackson may merge distinct Java members that share one external name into a single
   * {@link BeanPropertyDefinition}. Accessing the merged field or getter then throws; treat that as
   * a JSON:API member name collision rather than leaking Jackson's introspection exception.
   *
   * <p>This runs before unannotated properties are skipped. Field-only POJOs can leave no visible
   * role on the merged definition; mixed roles recovered from constructor parameters must still be
   * {@link MappingDiagnostic#NAME_COLLISION}, not {@link MappingDiagnostic#DUPLICATE_ROLE}. Jackson
   * may also attach multiple constructor parameters to one external name without throwing from
   * {@code getField}/{@code getGetter}; that is the same collision.
   */
  private static void rejectConflictingJacksonName(
      RoleAnnotations annotations, String jacksonName, Class<?> rawType) {
    if (!annotations.conflictingFields()) {
      return;
    }
    PropertyRole role = annotations.count() == 1 ? annotations.explicitRole() : null;
    if (role == PropertyRole.ATTRIBUTE) {
      throw new JsonApiMappingException(
          MappingDiagnostic.NAME_COLLISION,
          rawType,
          attributeLocation(jacksonName),
          "Duplicate attribute name: " + jacksonName);
    }
    if (role == PropertyRole.RELATIONSHIP) {
      throw new JsonApiMappingException(
          MappingDiagnostic.NAME_COLLISION,
          rawType,
          relationshipLocation(jacksonName),
          "Duplicate relationship name: " + jacksonName);
    }
    throw JsonApiMappingException.withoutLocation(
        MappingDiagnostic.NAME_COLLISION,
        rawType,
        "Duplicate JSON:API member name: " + jacksonName);
  }

  private static @Nullable PropertyRole resolveRole(
      RoleAnnotations annotations, String jacksonName, String logicalName, Class<?> rawType) {
    if (annotations.count() > 1) {
      // Conflicting role annotations leave no single wire member to point at; the property stays
      // identified in the message per the mapping-location contract.
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.DUPLICATE_ROLE,
          rawType,
          "Property '" + logicalName + "' has conflicting role annotations");
    }
    PropertyRole explicitRole = annotations.explicitRole();
    if (explicitRole != null) {
      return explicitRole;
    }
    if (JsonApiMembers.ID.equals(jacksonName)) {
      return PropertyRole.ID;
    }
    return null;
  }

  private static String resolveJsonapiName(
      RoleAnnotations annotations, String jacksonName, PropertyRole role) {
    return switch (role) {
      case ID, LOCAL_ID, ATTRIBUTE, RELATIONSHIP -> jacksonName;
      case RESOURCE_META -> JsonApiMembers.META;
      case RELATIONSHIP_META -> {
        JsonApiRelationshipMeta annotation = annotations.relationshipMeta();
        yield Objects.requireNonNull(annotation).relationship();
      }
    };
  }

  private static void validateJsonApiName(
      String jsonapiName, PropertyRole role, String logicalName, Class<?> rawType) {
    if (role == PropertyRole.RELATIONSHIP_META) {
      if (jsonapiName.isEmpty()) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.INVALID_RELATIONSHIP_META_TARGET,
            rawType,
            "@JsonApiRelationshipMeta.relationship() must not be empty for property '"
                + logicalName
                + "'");
      }
      return;
    }
    MappingDiagnostic diagnostic =
        switch (role) {
          case ATTRIBUTE -> MappingDiagnostic.INVALID_ATTRIBUTE_NAME;
          case RELATIONSHIP -> MappingDiagnostic.INVALID_RELATIONSHIP_NAME;
          default -> null;
        };
    if (diagnostic == null) {
      return;
    }
    if (jsonapiName.isEmpty() || isForbiddenMemberName(jsonapiName)) {
      // The wire name itself is invalid, so it cannot form a pointer segment; the offending name
      // and its logical property stay in the message.
      throw JsonApiMappingException.withoutLocation(
          diagnostic,
          rawType,
          "Invalid JSON:API member name '" + jsonapiName + "' for property '" + logicalName + "'");
    }
  }

  private static boolean isForbiddenMemberName(String jsonapiName) {
    return !MemberNames.isValid(jsonapiName)
        || JsonApiMembers.ID.equals(jsonapiName)
        || JsonApiMembers.TYPE.equals(jsonapiName);
  }

  private static void validatePropertyRoles(
      List<? extends MappingPropertyView> identifierProperties,
      List<? extends MappingPropertyView> localIdProperties,
      List<? extends MappingPropertyView> attributeProperties,
      List<? extends MappingPropertyView> relationshipProperties,
      List<? extends MappingPropertyView> resourceMetaProperties,
      List<? extends MappingPropertyView> relationshipMetaProperties,
      Class<?> rawType) {
    requireSingleIdentityRole(identifierProperties, localIdProperties, rawType);
    rejectDuplicateNames(
        attributeProperties, rawType, "attribute", MappingDefinitionResolver::attributeLocation);
    rejectDuplicateNames(
        relationshipProperties,
        rawType,
        "relationship",
        MappingDefinitionResolver::relationshipLocation);
    rejectAttributeRelationshipCollisions(attributeProperties, relationshipProperties, rawType);
    requireSingleResourceMeta(resourceMetaProperties, rawType);
    validateRelationshipMetaTargets(relationshipMetaProperties, relationshipProperties, rawType);
  }

  /**
   * Validates the two independent identity roles: at most one id property, at most one local-id
   * property, and at least one of the two overall. {@code id} and {@code lid} are distinct wire
   * members, so both roles may coexist on different logical properties; a property claiming both
   * roles fails earlier as conflicting role annotations.
   */
  private static void requireSingleIdentityRole(
      List<? extends MappingPropertyView> identifierProperties,
      List<? extends MappingPropertyView> localIdProperties,
      Class<?> rawType) {
    if (identifierProperties.size() > 1) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.DUPLICATE_ROLE,
          rawType,
          "Multiple id properties found for " + rawType.getName());
    }
    if (localIdProperties.size() > 1) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.DUPLICATE_ROLE,
          rawType,
          "Multiple local-id properties found for " + rawType.getName());
    }
    if (identifierProperties.isEmpty() && localIdProperties.isEmpty()) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.MISSING_IDENTIFIER,
          rawType,
          "No id or local-id property found for " + rawType.getName());
    }
  }

  private static void requireSingleResourceMeta(
      List<? extends MappingPropertyView> resourceMetaProperties, Class<?> rawType) {
    if (resourceMetaProperties.size() > 1) {
      throw new JsonApiMappingException(
          MappingDiagnostic.DUPLICATE_ROLE,
          rawType,
          RelationshipMetaSupport.resourceMetaLocation(),
          "Multiple resource meta properties found for "
              + rawType.getName()
              + "; at most one @JsonApiMeta property is allowed");
    }
  }

  private static List<MappingProperty> bindWriteRelationshipMeta(
      List<MappingProperty> relationshipMetaProperties,
      List<MappingProperty> relationshipProperties,
      Class<?> rawType) {
    if (relationshipMetaProperties.isEmpty()) {
      return List.of();
    }
    Map<String, MappingProperty> byIdentity = relationshipByIdentity(relationshipProperties);
    Set<String> seen = new HashSet<>();
    List<MappingProperty> bound = new ArrayList<>();
    for (MappingProperty property : relationshipMetaProperties) {
      MappingProperty target = requireRelationshipMetaTarget(property, byIdentity, seen, rawType);
      bound.add(
          new MappingProperty(
              property.definition(),
              property.accessor(),
              property.logicalName(),
              target.jsonapiName(),
              property.role()));
    }
    return bound;
  }

  private static <P extends MappingPropertyView> Map<String, P> relationshipByIdentity(
      List<P> relationshipProperties) {
    Map<String, P> byIdentity = new java.util.LinkedHashMap<>();
    for (P relationship : relationshipProperties) {
      byIdentity.put(relationship.logicalName(), relationship);
    }
    return byIdentity;
  }

  private static <P extends MappingPropertyView> P requireRelationshipMetaTarget(
      MappingPropertyView property,
      Map<String, P> relationshipsByIdentity,
      Set<String> seen,
      Class<?> rawType) {
    String identity = property.jsonapiName();
    P target = relationshipsByIdentity.get(identity);
    if (target == null) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.UNRESOLVED_RELATIONSHIP_META,
          rawType,
          "@JsonApiRelationshipMeta for property '"
              + property.logicalName()
              + "' references unknown relationship '"
              + identity
              + "' on "
              + rawType.getName());
    }
    if (!seen.add(identity)) {
      throw new JsonApiMappingException(
          MappingDiagnostic.DUPLICATE_ROLE,
          rawType,
          RelationshipMetaSupport.relationshipMetaLocation(target.jsonapiName()),
          "Multiple relationship meta properties target relationship '"
              + identity
              + "' on "
              + rawType.getName()
              + "; at most one is allowed");
    }
    return target;
  }

  private static void validateRelationshipMetaTargets(
      List<? extends MappingPropertyView> relationshipMetaProperties,
      List<? extends MappingPropertyView> relationshipProperties,
      Class<?> rawType) {
    if (relationshipMetaProperties.isEmpty()) {
      return;
    }
    Set<String> relationshipNames = new HashSet<>();
    for (MappingPropertyView relationship : relationshipProperties) {
      relationshipNames.add(relationship.jsonapiName());
    }
    Set<String> seen = new HashSet<>();
    for (MappingPropertyView property : relationshipMetaProperties) {
      String target = property.jsonapiName();
      if (!relationshipNames.contains(target)) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.UNRESOLVED_RELATIONSHIP_META,
            rawType,
            "@JsonApiRelationshipMeta for property '"
                + property.logicalName()
                + "' references unknown relationship '"
                + target
                + "' on "
                + rawType.getName());
      }
      if (!seen.add(target)) {
        throw new JsonApiMappingException(
            MappingDiagnostic.DUPLICATE_ROLE,
            rawType,
            RelationshipMetaSupport.relationshipMetaLocation(target),
            "Multiple relationship meta properties target relationship '"
                + target
                + "' on "
                + rawType.getName()
                + "; at most one is allowed");
      }
    }
  }

  private static void rejectDuplicateNames(
      List<? extends MappingPropertyView> properties,
      Class<?> rawType,
      String roleLabel,
      Function<String, MappingLocation> containerLocation) {
    Set<String> seen = new HashSet<>();
    for (MappingPropertyView property : properties) {
      if (!seen.add(property.jsonapiName())) {
        throw new JsonApiMappingException(
            MappingDiagnostic.NAME_COLLISION,
            rawType,
            containerLocation.apply(property.jsonapiName()),
            "Duplicate " + roleLabel + " name: " + property.jsonapiName());
      }
    }
  }

  private static void rejectAttributeRelationshipCollisions(
      List<? extends MappingPropertyView> attributeProperties,
      List<? extends MappingPropertyView> relationshipProperties,
      Class<?> rawType) {
    Set<String> relationshipNames = new HashSet<>();
    for (MappingPropertyView relationship : relationshipProperties) {
      relationshipNames.add(relationship.jsonapiName());
    }
    for (MappingPropertyView attribute : attributeProperties) {
      if (relationshipNames.contains(attribute.jsonapiName())) {
        // The colliding name could live under either container; no single member location applies.
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.NAME_COLLISION,
            rawType,
            "Attribute and relationship name collision: " + attribute.jsonapiName());
      }
    }
  }

  private static MappingLocation attributeLocation(String jsonapiName) {
    return MappingLocation.of("attributes", jsonapiName);
  }

  private static MappingLocation relationshipLocation(String jsonapiName) {
    return MappingLocation.of("relationships", jsonapiName, "data");
  }

  private record RoleAnnotations(
      @Nullable JsonApiId id,
      @Nullable JsonApiLocalId localId,
      @Nullable JsonApiAttribute attribute,
      @Nullable JsonApiRelationship relationship,
      @Nullable JsonApiMeta meta,
      @Nullable JsonApiRelationshipMeta relationshipMeta,
      boolean conflictingFields) {

    static RoleAnnotations from(BeanPropertyDefinition propertyDefinition) {
      return from(List.of(propertyDefinition));
    }

    static RoleAnnotations from(List<BeanPropertyDefinition> propertyDefinitions) {
      List<@Nullable AnnotatedMember> members = new ArrayList<>();
      boolean conflictingMembers = false;
      boolean multipleConstructorParameters = false;
      for (BeanPropertyDefinition propertyDefinition : propertyDefinitions) {
        conflictingMembers |= addMember(members, propertyDefinition::getField);
        conflictingMembers |= addMember(members, propertyDefinition::getGetter);
        conflictingMembers |= addMember(members, propertyDefinition::getSetter);
        int constructorParameters = 0;
        for (var parameters = propertyDefinition.getConstructorParameters();
            parameters.hasNext(); ) {
          members.add(parameters.next());
          constructorParameters++;
        }
        multipleConstructorParameters |= constructorParameters > 1;
      }
      return new RoleAnnotations(
          findAnnotationAnywhere(members, JsonApiId.class),
          findAnnotationAnywhere(members, JsonApiLocalId.class),
          findAnnotationAnywhere(members, JsonApiAttribute.class),
          findAnnotationAnywhere(members, JsonApiRelationship.class),
          findAnnotationAnywhere(members, JsonApiMeta.class),
          findAnnotationAnywhere(members, JsonApiRelationshipMeta.class),
          conflictingMembers || multipleConstructorParameters);
    }

    private static boolean addMember(
        List<@Nullable AnnotatedMember> members, Supplier<AnnotatedMember> accessor) {
      try {
        members.add(accessor.get());
        return false;
      } catch (IllegalArgumentException ex) {
        return true;
      }
    }

    private static <A extends Annotation> @Nullable A findAnnotationAnywhere(
        List<@Nullable AnnotatedMember> members, Class<A> annotationClass) {
      for (@Nullable AnnotatedMember member : members) {
        // Jackson's getAnnotation is not @Nullable-annotated; use hasAnnotation as the presence
        // check.
        if (member != null && member.hasAnnotation(annotationClass)) {
          return member.getAnnotation(annotationClass);
        }
      }
      return null;
    }

    int count() {
      return (id != null ? 1 : 0)
          + (localId != null ? 1 : 0)
          + (attribute != null ? 1 : 0)
          + (relationship != null ? 1 : 0)
          + (meta != null ? 1 : 0)
          + (relationshipMeta != null ? 1 : 0);
    }

    boolean hasAny() {
      return count() > 0;
    }

    @Nullable PropertyRole explicitRole() {
      if (id != null) {
        return PropertyRole.ID;
      }
      if (localId != null) {
        return PropertyRole.LOCAL_ID;
      }
      if (relationship != null) {
        return PropertyRole.RELATIONSHIP;
      }
      if (attribute != null) {
        return PropertyRole.ATTRIBUTE;
      }
      if (meta != null) {
        return PropertyRole.RESOURCE_META;
      }
      if (relationshipMeta != null) {
        return PropertyRole.RELATIONSHIP_META;
      }
      return null;
    }
  }
}

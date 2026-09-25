package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiLocalId;
import com.kazforge.jsonapi.annotation.JsonApiMeta;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiRelationshipMeta;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.JsonApiMembers;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.mapping.internal.MappingDefinitionInvariants;
import com.kazforge.jsonapi.mapping.internal.PropertyRole;
import com.kazforge.jsonapi.mapping.internal.SemanticProperty;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class MappingDefinitionResolver {

  private MappingDefinitionResolver() {}

  static ResourceMapping resolve(
      BeanDescription beanDescription, Class<?> rawType, AnnotatedClass resourceMetadata) {
    String resourceType =
        MappingDefinitionInvariants.requireResourceTypeName(
            resourceTypeName(resourceMetadata), rawType);

    List<BeanPropertyDefinition> propertyDefinitions = beanDescription.findProperties();
    ClassifiedProperties classified = classifyProperties(propertyDefinitions, rawType);
    List<MappingProperty> relationshipMeta =
        bindWriteRelationshipMeta(classified.relationshipMeta, classified.relationships, rawType);
    MappingDefinitionInvariants.validatePropertyRoles(
        classified.identifiers,
        classified.localIds,
        classified.attributes,
        classified.relationships,
        classified.resourceMeta,
        rawType);

    MappingProperty identifier = firstOrNull(classified.identifiers);
    MappingProperty localId = firstOrNull(classified.localIds);
    MappingProperty resourceMeta = firstOrNull(classified.resourceMeta);
    return new ResourceMapping(
        resourceType,
        identifier,
        localId,
        List.copyOf(classified.attributes),
        List.copyOf(classified.relationships),
        resourceMeta,
        List.copyOf(relationshipMeta),
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

  private static ClassifiedProperties classifyProperties(
      List<BeanPropertyDefinition> propertyDefinitions, Class<?> rawType) {
    ClassifiedProperties classified = new ClassifiedProperties();
    for (BeanPropertyDefinition propertyDefinition : propertyDefinitions) {
      ClassifiedProperty classifiedProperty = classifyProperty(propertyDefinition, rawType);
      if (classifiedProperty != null) {
        classified.add(classifiedProperty);
      }
    }
    return classified;
  }

  /**
   * Classifies one Jackson property into a write mapping member, or {@code null} when it does not
   * participate. Role and wire name resolve from annotations plus configured Jackson so
   * member-level declaration failures can report the member's resource-relative wire location even
   * when the accessor is missing. Unannotated properties do not participate. Merged Jackson names
   * are rejected before that skip so role-bearing collisions cannot disappear.
   *
   * <p>Relationship meta is classified in its unresolved form: the annotation names its target
   * relationship by Jackson logical identity, and only {@link #bindWriteRelationshipMeta} turns
   * that into final metadata carrying the target's JSON:API name.
   */
  private static @Nullable ClassifiedProperty classifyProperty(
      BeanPropertyDefinition propertyDefinition, Class<?> rawType) {
    RoleAnnotations annotations = RoleAnnotations.from(propertyDefinition);
    String externalName = propertyDefinition.getName();
    String logicalName = propertyDefinition.getInternalName();
    rejectConflictingJacksonName(annotations, externalName, rawType);
    PropertyRole role = resolveRole(annotations, externalName, logicalName, rawType);
    if (role == null) {
      return null;
    }
    String jsonapiName = resolveJsonapiName(annotations, externalName, role);
    MappingDefinitionInvariants.validateJsonApiName(jsonapiName, role, logicalName, rawType);
    AnnotatedMember accessor =
        requireAccessorIfAnnotated(
            propertyDefinition, logicalName, rawType, annotations, wireLocation(role, jsonapiName));
    if (accessor == null) {
      return null;
    }
    if (role == PropertyRole.RELATIONSHIP_META) {
      return new UnresolvedRelationshipMeta(
          propertyDefinition, accessor, logicalName, externalName, jsonapiName);
    }
    return new ResolvedProperty(
        new MappingProperty(
            propertyDefinition,
            accessor,
            new SemanticProperty(role, logicalName, externalName, jsonapiName)));
  }

  /** One classified write property; relationship meta stays unresolved until bound. */
  private sealed interface ClassifiedProperty
      permits ResolvedProperty, UnresolvedRelationshipMeta {}

  private record ResolvedProperty(MappingProperty property) implements ClassifiedProperty {}

  /**
   * An unresolved relationship-meta declaration. {@code targetIdentity} is the annotated target
   * relationship's Jackson logical identity, kept resolver-local until the match creates final
   * relationship-meta metadata with the target's JSON:API name.
   */
  private record UnresolvedRelationshipMeta(
      BeanPropertyDefinition definition,
      AnnotatedMember accessor,
      String logicalName,
      String externalName,
      String targetIdentity)
      implements ClassifiedProperty {}

  private static final class ClassifiedProperties {
    private final List<MappingProperty> identifiers = new ArrayList<>();
    private final List<MappingProperty> localIds = new ArrayList<>();
    private final List<MappingProperty> attributes = new ArrayList<>();
    private final List<MappingProperty> relationships = new ArrayList<>();
    private final List<MappingProperty> resourceMeta = new ArrayList<>();
    private final List<UnresolvedRelationshipMeta> relationshipMeta = new ArrayList<>();

    private void add(ClassifiedProperty classifiedProperty) {
      switch (classifiedProperty) {
        case ResolvedProperty(MappingProperty property) -> addResolved(property);
        case UnresolvedRelationshipMeta relationshipMetaProperty ->
            relationshipMeta.add(relationshipMetaProperty);
      }
    }

    private void addResolved(MappingProperty mappingProperty) {
      switch (mappingProperty.role()) {
        case ID -> identifiers.add(mappingProperty);
        case LOCAL_ID -> localIds.add(mappingProperty);
        case ATTRIBUTE -> attributes.add(mappingProperty);
        case RELATIONSHIP -> relationships.add(mappingProperty);
        case RESOURCE_META -> resourceMeta.add(mappingProperty);
        case RELATIONSHIP_META ->
            throw new IllegalStateException(
                "Resolved relationship meta must use the unresolved form");
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
      case ATTRIBUTE -> MappingDefinitionInvariants.attributeLocation(jsonapiName);
      case RELATIONSHIP -> MappingDefinitionInvariants.relationshipLocation(jsonapiName);
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
          MappingDefinitionInvariants.attributeLocation(jacksonName),
          "Duplicate attribute name: " + jacksonName);
    }
    if (role == PropertyRole.RELATIONSHIP) {
      throw new JsonApiMappingException(
          MappingDiagnostic.NAME_COLLISION,
          rawType,
          MappingDefinitionInvariants.relationshipLocation(jacksonName),
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
      RoleAnnotations annotations, String externalName, PropertyRole role) {
    return switch (role) {
      case ID -> JsonApiMembers.ID;
      case LOCAL_ID -> JsonApiMembers.LID;
      case ATTRIBUTE, RELATIONSHIP -> externalName;
      case RESOURCE_META -> JsonApiMembers.META;
      case RELATIONSHIP_META -> {
        JsonApiRelationshipMeta annotation = annotations.relationshipMeta();
        yield Objects.requireNonNull(annotation).relationship();
      }
    };
  }

  /**
   * Binds each relationship-meta property to its target relationship. {@code
   * JsonApiRelationshipMeta#relationship()} names the target by the relationship property's Java
   * logical identity (not its configured wire name); binding rewrites the meta property's {@code
   * jsonapiName} to that target's wire name, so a configured-Jackson rename carries the
   * relationship's meta automatically.
   */
  private static List<MappingProperty> bindWriteRelationshipMeta(
      List<UnresolvedRelationshipMeta> relationshipMetaProperties,
      List<MappingProperty> relationshipProperties,
      Class<?> rawType) {
    if (relationshipMetaProperties.isEmpty()) {
      return List.of();
    }
    Set<String> seen = new HashSet<>();
    List<MappingProperty> bound = new ArrayList<>();
    for (UnresolvedRelationshipMeta property : relationshipMetaProperties) {
      SemanticProperty metadata =
          MappingDefinitionInvariants.resolveRelationshipMeta(
              property.logicalName(),
              property.externalName(),
              property.targetIdentity(),
              relationshipProperties,
              seen,
              rawType);
      bound.add(new MappingProperty(property.definition(), property.accessor(), metadata));
    }
    return bound;
  }

  /**
   * Resolves the minimum deserialization-aware mapping view needed by ordinary flat reads.
   *
   * <p>Role and JSON:API wire-name interpretation is shared with the write mapping, but the
   * property definitions and effective target types come from Jackson's deserialization side. The
   * serialization definitions are retained only to preserve role annotations and to diagnose a
   * supplied member whose serialization-only declaration is absent from the effective read model.
   */
  static ReadResourceMapping resolveRead(
      BeanDescription deserializationDescription,
      BeanDescription serializationDescription,
      Class<?> rawType,
      AnnotatedClass resourceMetadata,
      MappingDefinitionCache.EffectiveReadProperties effective) {
    String resourceType =
        MappingDefinitionInvariants.requireResourceTypeName(
            resourceTypeName(resourceMetadata), rawType);
    List<ReadMappingProperty> identifierProperties = new ArrayList<>();
    List<ReadMappingProperty> localIdProperties = new ArrayList<>();
    List<ReadMappingProperty> attributeProperties = new ArrayList<>();
    List<ReadMappingProperty> relationshipProperties = new ArrayList<>();
    List<ReadMappingProperty> resourceMetaProperties = new ArrayList<>();
    List<UnresolvedReadRelationshipMeta> relationshipMetaProperties = new ArrayList<>();

    for (PropertyPair pair :
        mergeProperties(deserializationDescription, serializationDescription)) {
      BeanPropertyDefinition propertyDefinition = pair.primary();
      RoleAnnotations annotations = RoleAnnotations.from(pair.definitions());
      String externalName = propertyDefinition.getName();
      String logicalName = propertyDefinition.getInternalName();
      rejectConflictingJacksonName(annotations, externalName, rawType);
      PropertyRole role = resolveRole(annotations, externalName, logicalName, rawType);
      if (role == null) {
        continue;
      }
      String jsonapiName = resolveJsonapiName(annotations, externalName, role);
      MappingDefinitionInvariants.validateJsonApiName(jsonapiName, role, logicalName, rawType);
      AnnotatedMember serializationMember =
          pair.serialization() == null ? null : pair.serialization().getAccessor();
      SettableBeanProperty effectiveProperty = effective.byExternalName().get(externalName);
      if (role == PropertyRole.RELATIONSHIP_META) {
        relationshipMetaProperties.add(
            new UnresolvedReadRelationshipMeta(
                propertyDefinition,
                serializationMember,
                effectiveProperty,
                logicalName,
                externalName,
                jsonapiName));
      } else {
        ReadMappingProperty mappingProperty =
            new ReadMappingProperty(
                propertyDefinition,
                serializationMember,
                effectiveProperty,
                new SemanticProperty(role, logicalName, externalName, jsonapiName));
        switch (role) {
          case ID -> identifierProperties.add(mappingProperty);
          case LOCAL_ID -> localIdProperties.add(mappingProperty);
          case ATTRIBUTE -> attributeProperties.add(mappingProperty);
          case RELATIONSHIP -> relationshipProperties.add(mappingProperty);
          case RESOURCE_META -> resourceMetaProperties.add(mappingProperty);
          case RELATIONSHIP_META ->
              throw new IllegalStateException("Relationship meta must use the unresolved form");
        }
      }
    }
    List<ReadMappingProperty> boundRelationshipMeta =
        bindReadRelationshipMeta(relationshipMetaProperties, relationshipProperties, rawType);
    MappingDefinitionInvariants.validatePropertyRoles(
        identifierProperties,
        localIdProperties,
        attributeProperties,
        relationshipProperties,
        resourceMetaProperties,
        rawType);

    ReadMappingProperty identifier = firstOrNull(identifierProperties);
    ReadMappingProperty localId = firstOrNull(localIdProperties);
    ReadMappingProperty resourceMeta = firstOrNull(resourceMetaProperties);
    return new ReadResourceMapping(
        resourceType,
        identifier,
        localId,
        List.copyOf(attributeProperties),
        List.copyOf(relationshipProperties),
        resourceMeta,
        List.copyOf(boundRelationshipMeta),
        effective.creatorExternalNames());
  }

  /**
   * Rejects conflicting Jackson member definitions before any deserializer is built, so a duplicate
   * declaration surfaces as the shared name-collision diagnostic instead of leaking Jackson's
   * introspection exception. Role classification performs the same check later; this pre-pass
   * exists because effective-property resolution can fail first on the same declaration.
   */
  static void rejectConflicts(
      BeanDescription deserializationDescription,
      BeanDescription serializationDescription,
      Class<?> rawType) {
    List<PropertyPair> pairs;
    try {
      pairs = mergeProperties(deserializationDescription, serializationDescription);
    } catch (IllegalArgumentException e) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.NAME_COLLISION,
          rawType,
          "Conflicting property definitions on " + rawType.getName());
    }
    for (PropertyPair pair : pairs) {
      rejectConflictingJacksonName(
          RoleAnnotations.from(pair.definitions()), pair.primary().getName(), rawType);
    }
  }

  private static List<PropertyPair> mergeProperties(
      BeanDescription deserializationDescription, BeanDescription serializationDescription) {
    List<PropertyPair> pairs = new ArrayList<>();
    for (BeanPropertyDefinition definition : deserializationDescription.findProperties()) {
      pairs.add(new PropertyPair(definition, null));
    }
    for (BeanPropertyDefinition definition : serializationDescription.findProperties()) {
      // Logical identity takes precedence because externally configured names can cross between
      // properties. External names are only a fallback when they identify one deserialization
      // property; a second serialization definition never overwrites an existing pairing.
      PropertyPair match =
          findUniqueDeserializationMatch(
              pairs, definition, BeanPropertyDefinition::getInternalName);
      if (match == null) {
        match = findUniqueDeserializationMatch(pairs, definition, BeanPropertyDefinition::getName);
      }
      if (match == null || match.serialization() != null) {
        pairs.add(new PropertyPair(null, definition));
      } else {
        match.setSerialization(definition);
      }
    }
    return pairs;
  }

  private static @Nullable PropertyPair findUniqueDeserializationMatch(
      List<PropertyPair> pairs,
      BeanPropertyDefinition candidate,
      Function<BeanPropertyDefinition, String> nameExtractor) {
    String candidateName = nameExtractor.apply(candidate);
    PropertyPair match = null;
    for (PropertyPair pair : pairs) {
      if (!pair.matchesDeserializationName(candidateName, nameExtractor)) {
        continue;
      }
      if (match != null) {
        return null;
      }
      match = pair;
    }
    return match;
  }

  private static final class PropertyPair {

    private final @Nullable BeanPropertyDefinition deserialization;
    private @Nullable BeanPropertyDefinition serialization;

    PropertyPair(
        @Nullable BeanPropertyDefinition deserialization,
        @Nullable BeanPropertyDefinition serialization) {
      this.deserialization = deserialization;
      this.serialization = serialization;
    }

    @Nullable BeanPropertyDefinition serialization() {
      return serialization;
    }

    void setSerialization(BeanPropertyDefinition definition) {
      serialization = definition;
    }

    boolean matchesDeserializationName(
        String candidateName, Function<BeanPropertyDefinition, String> nameExtractor) {
      return deserialization != null && nameExtractor.apply(deserialization).equals(candidateName);
    }

    BeanPropertyDefinition primary() {
      return deserialization != null
          ? deserialization
          : Objects.requireNonNull(serialization, "serialization");
    }

    List<BeanPropertyDefinition> definitions() {
      if (deserialization != null && serialization != null) {
        return List.of(deserialization, serialization);
      }
      return List.of(primary());
    }
  }

  /** An unresolved read relationship-meta declaration, bound after its target is matched. */
  private record UnresolvedReadRelationshipMeta(
      BeanPropertyDefinition definition,
      @Nullable AnnotatedMember serializationMember,
      @Nullable SettableBeanProperty effectiveProperty,
      String logicalName,
      String externalName,
      String targetIdentity) {}

  private static List<ReadMappingProperty> bindReadRelationshipMeta(
      List<UnresolvedReadRelationshipMeta> relationshipMetaProperties,
      List<ReadMappingProperty> relationshipProperties,
      Class<?> rawType) {
    if (relationshipMetaProperties.isEmpty()) {
      return List.of();
    }
    Set<String> seen = new HashSet<>();
    List<ReadMappingProperty> bound = new ArrayList<>();
    for (UnresolvedReadRelationshipMeta property : relationshipMetaProperties) {
      SemanticProperty metadata =
          MappingDefinitionInvariants.resolveRelationshipMeta(
              property.logicalName(),
              property.externalName(),
              property.targetIdentity(),
              relationshipProperties,
              seen,
              rawType);
      bound.add(
          new ReadMappingProperty(
              property.definition(),
              property.serializationMember(),
              property.effectiveProperty(),
              metadata));
    }
    return bound;
  }

  /** The single classified property for a role, or {@code null} when the role is absent. */
  private static <P> @Nullable P firstOrNull(List<P> properties) {
    return properties.isEmpty() ? null : properties.getFirst();
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

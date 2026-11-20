package com.kazforge.jsonapi.jackson3.internal.mapping;

import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.jackson3.mapping.RelationshipLinkageMapper;
import com.kazforge.jsonapi.mapping.BindingSource;
import com.kazforge.jsonapi.mapping.BindingSource.Presence;
import com.kazforge.jsonapi.mapping.BoundResource;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializer;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.deser.impl.NullsConstantProvider;
import tools.jackson.databind.deser.jdk.CollectionDeserializer;
import tools.jackson.databind.deser.jdk.JDKValueInstantiators;
import tools.jackson.databind.deser.jdk.ObjectArrayDeserializer;
import tools.jackson.databind.deser.jdk.PrimitiveArrayDeserializers;
import tools.jackson.databind.deser.jdk.StringArrayDeserializer;
import tools.jackson.databind.deser.jdk.StringCollectionDeserializer;
import tools.jackson.databind.deser.std.ContainerDeserializerBase;
import tools.jackson.databind.deser.std.NullifyingDeserializer;
import tools.jackson.databind.ext.jdk8.Jdk8OptionalDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ser.BeanSerializer;
import tools.jackson.databind.ser.UnrolledBeanSerializer;

/**
 * Opt-in source snapshot assembly after successful binding, using the operation's resolved
 * definition and effective configured conversion shapes. No conversion, DTO access, or wire re-read
 * occurs here. The returned tree retains names, presence and pointers only.
 */
final class BindingSources {

  private static final MappingLocation DATA = MappingLocation.of("data");
  private static final Node OMITTED = new Node(new BindingSource(Presence.OMITTED, null), Map.of());
  private static final Node UNRESOLVED =
      new Node(new BindingSource(Presence.UNRESOLVED, null), Map.of());
  private static final ContentNullPolicy CONTENT_NULLS = new ContentNullPolicy();

  private final JsonMapper mapper;
  private final EffectiveReadShapes shapes;
  private final Map<Class<?>, RelationshipLinkageMapper> linkageMappers;
  private final @Nullable StructuredValueBinder structured;

  BindingSources(
      JsonMapper mapper,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers,
      @Nullable StructuredValueBinder structured) {
    this.mapper = mapper;
    this.shapes = new EffectiveReadShapes(mapper);
    this.linkageMappers = linkageMappers;
    this.structured = structured;
  }

  <T> BoundResource<T> read(
      T dto,
      ResourceObject resource,
      ReadResourceDefinition<ReadMappingProperty> definition,
      JavaType dtoType) {
    Map<String, SettableBeanProperty> effective = effectiveProperties(dtoType);
    Map<String, Node> children = new LinkedHashMap<>();
    addRead(children, resource, definition.identifier(), effective);
    addRead(children, resource, definition.localId(), effective);
    definition.attributes().forEach(property -> addRead(children, resource, property, effective));
    definition
        .relationships()
        .forEach(property -> addRead(children, resource, property, effective));
    addRead(children, resource, definition.resourceMeta(), effective);
    definition
        .relationshipMetaProperties()
        .forEach(property -> addRead(children, resource, property, effective));
    return new Snapshot<>(dto, present(DATA, false, children));
  }

  private void addRead(
      Map<String, Node> children,
      ResourceObject resource,
      @Nullable ReadProperty<ReadMappingProperty> property,
      Map<String, SettableBeanProperty> effective) {
    if (property != null) {
      children.put(
          property.logicalName(),
          root(resource, property.token(), effective.get(property.externalName()), false));
    }
  }

  <T> BoundResource<T> patch(
      T dto,
      ResourceObject resource,
      TypedPatchDefinition<MappingProperty, JavaType> definition,
      JavaType dtoType) {
    Map<String, SettableBeanProperty> effective = effectiveProperties(dtoType);
    Map<String, Node> children = new LinkedHashMap<>();
    addPatch(children, resource, definition.identifier(), effective);
    definition.attributes().forEach(property -> addPatch(children, resource, property, effective));
    definition
        .relationships()
        .forEach(property -> addPatch(children, resource, property, effective));
    addPatch(children, resource, definition.resourceMeta(), effective);
    definition
        .relationshipMetaProperties()
        .forEach(property -> addPatch(children, resource, property, effective));
    return new Snapshot<>(dto, present(DATA, false, children));
  }

  private Map<String, SettableBeanProperty> effectiveProperties(JavaType type) {
    Map<String, SettableBeanProperty> effective = new LinkedHashMap<>();
    ValueDeserializer<?> deserializer = rootDeserializer(type);
    if (deserializer instanceof BeanDeserializer bean
        && deserializer.getClass() == BeanDeserializer.class) {
      for (var member : shapes.members(type, bean)) {
        if (member.logicalName() != null) {
          effective.put(member.wireName(), member.property());
        }
      }
    }
    return effective;
  }

  private void addPatch(
      Map<String, Node> children,
      ResourceObject resource,
      @Nullable TypedPatchProperty<MappingProperty, JavaType> property,
      Map<String, SettableBeanProperty> effective) {
    if (property != null) {
      children.put(
          property.logicalName(),
          root(resource, property.token(), effective.get(property.externalName()), true));
    }
  }

  private Node root(
      ResourceObject resource,
      MappingPropertyView property,
      @Nullable SettableBeanProperty effective,
      boolean patch) {
    if (effective == null || effective.isIgnorable()) {
      return UNRESOLVED;
    }
    Input input = input(resource, property.metadata());
    if (patch && property.role() == PropertyRole.RELATIONSHIP_META) {
      Relationship relationship = relationship(resource, property.jsonapiName());
      if (relationship != null && relationship.data() == null && relationship.meta() != null) {
        return UNRESOLVED;
      }
    }
    if (!input.supplied()) {
      return OMITTED;
    }
    if (property.role() == PropertyRole.RELATIONSHIP
        && input.wire() instanceof RelationshipData data) {
      JavaType type = MappingTypeSupport.unwrapTransportWrappers(effective.getType());
      if (customized(effective)) {
        return present(input.location(), false, Map.of());
      }
      ReadRelationshipShape<JavaType> shape =
          RelationshipLinkageSupport.readRelationshipShape(
              type, property, linkageMappers, mapper.getTypeFactory());
      if (shape instanceof ReadRelationshipShape.Mapped<JavaType>) {
        return present(input.location(), false, Map.of());
      }
      BeanDeserializer bean =
          linkageBean(effective.getType(), effective.getValueDeserializer(), effective);
      if (bean == null) {
        return present(input.location(), false, Map.of());
      }
      return linkage(data, type, shape, bean, input.location());
    }
    return value(
        input.wire(),
        effective.getType(),
        effective.getValueDeserializer(),
        effective,
        input.location());
  }

  private static Input input(ResourceObject resource, SemanticProperty property) {
    String name = property.jsonapiName();
    return switch (property.role()) {
      case ID -> new Input(resource.hasId(), resource.id(), DATA.append("id"));
      case LOCAL_ID -> new Input(resource.hasLid(), resource.lid(), DATA.append("lid"));
      case ATTRIBUTE ->
          member(
              resource.attributes() == null ? null : resource.attributes().attributes(),
              name,
              DATA.append("attributes").append(name));
      case RESOURCE_META -> meta(resource.meta(), DATA.append("meta"));
      case RELATIONSHIP -> {
        Relationship relationship = relationship(resource, name);
        RelationshipData data = relationship == null ? null : relationship.data();
        yield new Input(
            data != null,
            data instanceof RelationshipData.NullLinkage ? null : data,
            DATA.append("relationships").append(name).append("data"));
      }
      case RELATIONSHIP_META -> {
        Relationship relationship = relationship(resource, name);
        yield meta(
            relationship == null ? null : relationship.meta(),
            DATA.append("relationships").append(name).append("meta"));
      }
    };
  }

  private static @Nullable Relationship relationship(ResourceObject resource, String name) {
    return resource.relationships() == null
        ? null
        : resource.relationships().relationships().get(name);
  }

  private static Input meta(@Nullable Meta meta, MappingLocation location) {
    return new Input(meta != null, meta == null ? null : meta.members(), location);
  }

  private static Input member(@Nullable Map<?, ?> members, String name, MappingLocation location) {
    return new Input(
        members != null && members.containsKey(name),
        members == null ? null : members.get(name),
        location);
  }

  private Node value(
      @Nullable Object wire,
      JavaType type,
      @Nullable ValueDeserializer<?> deserializer,
      @Nullable SettableBeanProperty property,
      MappingLocation location) {
    if (wire == null) {
      return present(location, true, Map.of());
    }
    if (deserializer == null || (property != null && property.getValueTypeDeserializer() != null)) {
      return present(location, false, Map.of());
    }
    Map<String, Node> children = Map.of();
    if (WrapperCustomization.isPatchPresence(type)
        && deserializer instanceof PatchPresenceDeserializer) {
      JavaType inner = type.containedType(0);
      return value(wire, inner, rootDeserializer(inner), null, location);
    }
    if (type.getRawClass() == Optional.class
        && type.containedTypeCount() == 1
        && deserializer.getClass() == Jdk8OptionalDeserializer.class
        && (property == null || !customized(property))) {
      JavaType inner = type.containedType(0);
      if (polymorphic(inner)) {
        return present(location, false, Map.of());
      }
      return value(
          wire,
          inner,
          mapper._deserializationContext().findContextualValueDeserializer(inner, property),
          property,
          location);
    }
    if (wire instanceof Map<?, ?> object
        && deserializer.getClass() == BeanDeserializer.class
        && !((BeanDeserializerBase) deserializer).getValueInstantiator().canCreateUsingDelegate()) {
      children = bean(object, type, (BeanDeserializerBase) deserializer, location);
    } else if (wire instanceof List<?> array && indexed(type, deserializer, property)) {
      children = array(array, type, deserializer, property, location);
    }
    return present(location, false, children);
  }

  private Map<String, Node> bean(
      Map<?, ?> wire, JavaType type, BeanDeserializerBase deserializer, MappingLocation location) {
    Map<String, Node> children = new LinkedHashMap<>();
    StructuredShape<JavaType> typedShape = structured == null ? null : structured.shapeOf(type);
    for (var member : shapes.members(type, deserializer)) {
      String logicalName = member.logicalName();
      String wireName = member.wireName();
      if (logicalName == null || wireName.isEmpty()) {
        continue;
      }
      SettableBeanProperty property = member.property();
      Node node;
      if (ambiguous(wire, property, deserializer.isCaseInsensitive())) {
        node = UNRESOLVED;
      } else if (typedShape != null
          && typedShape.presenceAware()
          && typedShape.memberByWire(wireName) == null) {
        node = UNRESOLVED;
      } else if (!wire.containsKey(wireName)) {
        node = OMITTED;
      } else if (property.getValueTypeDeserializer() != null) {
        node = present(location.append(wireName), wire.get(wireName) == null, Map.of());
      } else {
        node =
            value(
                wire.get(wireName),
                member.type(),
                property.getValueDeserializer(),
                property,
                location.append(wireName));
      }
      if (children.putIfAbsent(logicalName, node) != null) {
        children.put(logicalName, UNRESOLVED);
      }
    }
    return children;
  }

  /** Accepted alternative spellings are not guessed to be the property's canonical wire member. */
  private boolean ambiguous(
      Map<?, ?> wire, SettableBeanProperty property, boolean caseInsensitive) {
    Locale locale = mapper.deserializationConfig().getLocale();
    for (Object key : wire.keySet()) {
      if (key instanceof String name
          && !name.equals(property.getName())
          && ((caseInsensitive && folded(name, locale).equals(folded(property.getName(), locale)))
              || property.findAliases(mapper.deserializationConfig()).stream()
                  .anyMatch(alias ->
                      caseInsensitive
                          ? folded(name, locale).equals(folded(alias.getSimpleName(), locale))
                          : name.equals(alias.getSimpleName())))) {
        return true;
      }
    }
    return false;
  }

  /** Jackson's case-insensitive lookup lowercases with the configured locale. */
  private static String folded(String name, Locale locale) {
    return name.toLowerCase(locale);
  }

  private boolean indexed(
      JavaType type, ValueDeserializer<?> deserializer, @Nullable SettableBeanProperty property) {
    if ((property != null && (property.isMerging() || property.getValueTypeDeserializer() != null))
        || CONTENT_NULLS.skips(mapper._deserializationContext(), property)) {
      return false;
    }
    Class<?> implementation = deserializer.getClass();
    if (type.isArrayType()) {
      return implementation == ObjectArrayDeserializer.class
          || implementation == StringArrayDeserializer.class
          || implementation.getEnclosingClass() == PrimitiveArrayDeserializers.class;
    }
    if (!List.class.isAssignableFrom(type.getRawClass())
        || (implementation != CollectionDeserializer.class
            && implementation != StringCollectionDeserializer.class)) {
      return false;
    }
    ContainerDeserializerBase<?> container = (ContainerDeserializerBase<?>) deserializer;
    var instantiator = container.getValueInstantiator();
    var standard =
        instantiator == null
            ? null
            : JDKValueInstantiators.findStdValueInstantiator(
                mapper.deserializationConfig(), instantiator.getValueClass());
    if (standard == null || instantiator.getClass() != standard.getClass()) {
      return false;
    }
    // Buffered object-identity elements need not finish at their input positions.
    ValueDeserializer<Object> content = container.getContentDeserializer();
    return content == null || content.getObjectIdReader(mapper._deserializationContext()) == null;
  }

  private Map<String, Node> array(
      List<?> wire,
      JavaType type,
      ValueDeserializer<?> deserializer,
      @Nullable SettableBeanProperty property,
      MappingLocation location) {
    Map<String, Node> children = new LinkedHashMap<>();
    JavaType content = type.getContentType();
    boolean polymorphic = polymorphic(content);
    ValueDeserializer<?> contentDeserializer =
        deserializer instanceof ContainerDeserializerBase<?> container
            ? container.getContentDeserializer()
            : mapper._deserializationContext().findContextualValueDeserializer(content, property);
    for (int index = 0; index < wire.size(); index++) {
      String segment = Integer.toString(index);
      children.put(
          segment,
          polymorphic
              ? present(location.append(segment), wire.get(index) == null, Map.of())
              : value(
                  wire.get(index),
                  content,
                  contentDeserializer,
                  property,
                  location.append(segment)));
    }
    return children;
  }

  /** Final Jackson conversion must preserve the built-in linkage/occurrence structure too. */
  private @Nullable BeanDeserializer linkageBean(
      JavaType type,
      @Nullable ValueDeserializer<?> deserializer,
      @Nullable SettableBeanProperty property) {
    if (deserializer == null) {
      return null;
    }
    if (WrapperCustomization.isPatchPresence(type)
        && deserializer instanceof PatchPresenceDeserializer) {
      JavaType inner = type.containedType(0);
      return linkageBean(inner, rootDeserializer(inner), null);
    }
    if (type.getRawClass() == Optional.class
        && type.containedTypeCount() == 1
        && deserializer.getClass() == Jdk8OptionalDeserializer.class) {
      JavaType inner = type.containedType(0);
      return linkageBean(
          inner,
          mapper._deserializationContext().findContextualValueDeserializer(inner, property),
          property);
    }
    if (type.isContainerType()) {
      if (!indexed(type, deserializer, property) || polymorphic(type.getContentType())) {
        return null;
      }
      JavaType content = type.getContentType();
      return linkageBean(
          content,
          ((ContainerDeserializerBase<?>) deserializer).getContentDeserializer(),
          property);
    }
    return ordinaryLinkageBean(type, deserializer);
  }

  private @Nullable BeanDeserializer ordinaryLinkageBean(
      JavaType type, ValueDeserializer<?> deserializer) {
    Class<?> serializer = mapper._serializationContext().findRootValueSerializer(type).getClass();
    if (deserializer.getClass() != BeanDeserializer.class
        || (serializer != BeanSerializer.class && serializer != UnrolledBeanSerializer.class)) {
      return null;
    }
    var members = shapes.members(type, (BeanDeserializerBase) deserializer);
    List<String> required =
        MappingTypeSupport.isLinkageType(type)
            ? List.of("target", "meta")
            : List.of("type", "id", "lid", "meta");
    Set<String> names = new HashSet<>();
    for (var member : members) {
      String logicalName = member.logicalName();
      if (logicalName != null) {
        names.add(logicalName);
      }
    }
    if (!names.containsAll(required)) {
      return null;
    }
    if (MappingTypeSupport.isLinkageType(type)) {
      return members.stream().noneMatch(member -> customized(member.property()))
          ? (BeanDeserializer) deserializer
          : null;
    }
    return type.getRawClass() == ResourceIdentifier.class ? (BeanDeserializer) deserializer : null;
  }

  private Node linkage(
      RelationshipData data,
      JavaType type,
      ReadRelationshipShape<JavaType> shape,
      BeanDeserializer bean,
      MappingLocation location) {
    Map<String, Node> children = new LinkedHashMap<>();
    if (data instanceof RelationshipData.SingleLinkage(ResourceIdentifier identifier)) {
      return occurrence(identifier, type, shape, bean, location);
    }
    if (data
        instanceof
        RelationshipData.IdentifierCollectionLinkage(List<ResourceIdentifier> identifiers)) {
      for (int index = 0; index < identifiers.size(); index++) {
        String segment = Integer.toString(index);
        children.put(
            segment,
            occurrence(
                identifiers.get(index),
                type.getContentType(),
                shape,
                bean,
                location.append(segment)));
      }
    }
    return present(location, false, children);
  }

  private Node occurrence(
      ResourceIdentifier identifier,
      JavaType type,
      ReadRelationshipShape<JavaType> shape,
      BeanDeserializer bean,
      MappingLocation location) {
    Map<String, Node> children = new LinkedHashMap<>();
    if (shape
        instanceof
        ReadRelationshipShape.Wrapped<JavaType>(
            boolean toMany,
            JavaType ignoredMeta,
            ReadRelationshipShape<JavaType> targetShape)) {
      addWrapped(children, identifier, type, toMany, targetShape, bean, location);
    } else {
      addDirectIdentifier(children, identifier, location);
    }
    return present(location, false, children);
  }

  private void addWrapped(
      Map<String, Node> children,
      ResourceIdentifier identifier,
      JavaType type,
      boolean toMany,
      ReadRelationshipShape<JavaType> targetShape,
      BeanDeserializer bean,
      MappingLocation location) {
    // A nullable to-one mapper may short-circuit the wrapper before its meta is bound.
    if (!toMany && targetShape instanceof ReadRelationshipShape.Mapped<JavaType>) {
      return;
    }
    Node targetNode = UNRESOLVED;
    if (targetShape instanceof ReadRelationshipShape.Direct<JavaType>) {
      JavaType targetType = MappingTypeSupport.linkageTargetType(type);
      SettableBeanProperty target =
          shapes.members(type, bean).stream()
              .filter(member -> "target".equals(member.logicalName()))
              .map(EffectiveReadShapes.Member::property)
              .findFirst()
              .orElseThrow();
      BeanDeserializer targetBean = linkageBean(targetType, target.getValueDeserializer(), target);
      if (targetBean != null) {
        targetNode = occurrence(identifier, targetType, targetShape, targetBean, location);
      }
    }
    children.put("target", targetNode);
    // Identifier meta converts once before the wrapper record converts again; a custom
    // serializer/deserializer pair may change member correspondence, so only the object is exact.
    children.put(
        "meta",
        identifier.meta() == null ? OMITTED : present(location.append("meta"), false, Map.of()));
  }

  private static void addDirectIdentifier(
      Map<String, Node> children, ResourceIdentifier identifier, MappingLocation location) {
    children.put("type", present(location.append("type"), false, Map.of()));
    children.put(
        "id", identifier.id() == null ? OMITTED : present(location.append("id"), false, Map.of()));
    children.put(
        "lid",
        identifier.lid() == null ? OMITTED : present(location.append("lid"), false, Map.of()));
    children.put(
        "meta",
        identifier.meta() == null ? OMITTED : present(location.append("meta"), false, Map.of()));
  }

  private boolean customized(SettableBeanProperty property) {
    return property.getValueTypeDeserializer() != null
        || WrapperCustomization.hasDeserialization(
            mapper, property.getType(), property.getMember());
  }

  private boolean polymorphic(JavaType type) {
    return type.getTypeHandler() != null
        || mapper._deserializationContext().findTypeDeserializer(type) != null;
  }

  private ValueDeserializer<?> rootDeserializer(JavaType type) {
    return mapper._deserializationContext().findRootValueDeserializer(type);
  }

  private static Node present(
      MappingLocation location, boolean isNull, Map<String, Node> children) {
    return new Node(
        new BindingSource(isNull ? Presence.PRESENT_NULL : Presence.PRESENT_VALUE, location),
        children);
  }

  private record Input(boolean supplied, @Nullable Object wire, MappingLocation location) {}

  /** Native null policy with inert content: inspecting it never constructs an application value. */
  private static final class ContentNullPolicy extends NullifyingDeserializer {
    private boolean skips(DeserializationContext context, @Nullable SettableBeanProperty property) {
      return NullsConstantProvider.isSkipper(
          findContentNullProvider(context, property, NullifyingDeserializer.instance));
    }
  }

  private record Node(BindingSource source, Map<String, Node> children) {
    private Node {
      children = Map.copyOf(children);
    }
  }

  private record Snapshot<T>(T resource, Node root) implements BoundResource<T> {
    @Override
    public BindingSource source(List<String> logicalPath) {
      Objects.requireNonNull(logicalPath, "logicalPath");
      logicalPath.forEach(segment -> Objects.requireNonNull(segment, "logicalPath segment"));
      Node current = root;
      for (String segment : logicalPath) {
        current = current.children().getOrDefault(segment, UNRESOLVED);
      }
      return current.source();
    }
  }
}

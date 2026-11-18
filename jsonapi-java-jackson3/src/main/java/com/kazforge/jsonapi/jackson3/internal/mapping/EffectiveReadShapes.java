package com.kazforge.jsonapi.jackson3.internal.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.json.JsonMapper;

/**
 * Effective bean properties shared by construction-path translation and successful-binding source
 * capture. Configured deserializers own eligibility and types; introspection supplies logical names
 * only, never fallback deserialization targets.
 */
final class EffectiveReadShapes {

  private final JsonMapper mapper;

  EffectiveReadShapes(JsonMapper mapper) {
    this.mapper = mapper;
  }

  List<Member> members(JavaType type, BeanDeserializerBase bean) {
    var introspector = mapper.deserializationConfig().classIntrospectorInstance();
    var description =
        introspector.introspectForDeserialization(
            type, introspector.introspectClassAnnotations(type));
    Map<String, String> logicalNames = new HashMap<>();
    for (var definition : description.findProperties()) {
      if (!description.getIgnoredPropertyNames().contains(definition.getName())) {
        logicalNames.put(definition.getName(), definition.getInternalName());
      }
    }
    List<Member> members = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Class<?> activeView = mapper.deserializationConfig().getActiveView();
    addMembers(bean.properties(), members, seen, activeView, logicalNames);
    // Creator-only remnants remain diagnostic paths, not proof of ordinary wire membership.
    addMembers(bean.creatorProperties(), members, seen, activeView, Map.of());
    return List.copyOf(members);
  }

  private static void addMembers(
      Iterator<SettableBeanProperty> properties,
      List<Member> members,
      Set<String> seen,
      @Nullable Class<?> activeView,
      Map<String, String> logicalNames) {
    while (properties.hasNext()) {
      SettableBeanProperty property = properties.next();
      if (!property.isInjectionOnly()
          && (activeView == null || property.visibleInView(activeView))
          && seen.add(property.getName())) {
        members.add(
            new Member(
                property.isIgnorable() ? null : logicalNames.get(property.getName()), property));
      }
    }
  }

  record Member(@Nullable String logicalName, SettableBeanProperty property) {
    String wireName() {
      return property.getName();
    }

    JavaType type() {
      return property.getType();
    }

    boolean walkable() {
      return type().getRawClass() == Optional.class
          || property.getValueDeserializer() instanceof BeanDeserializerBase;
    }
  }
}

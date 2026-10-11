package com.kazforge.jsonapi.jackson3.internal;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.json.JsonMapper;

/**
 * Effective creator external names for typed PATCH DTO construction, resolved from the configured
 * bean deserializer like ordinary flat reads. Only non-injection, view-eligible effective creator
 * properties are covered; those names drive message-independent missing-creator-input
 * classification in {@link BeanConstruction}.
 */
final class PatchCreatorNames {

  private final JsonMapper mapper;
  private final Map<CacheKey, Set<String>> cache = new ConcurrentHashMap<>();

  PatchCreatorNames(JsonMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  Set<String> creatorNames(JavaType javaType) {
    DeserializationConfig config = mapper.deserializationConfig();
    CacheKey key = new CacheKey(javaType, configHash(config));
    Set<String> cached = cache.get(key);
    if (cached != null) {
      return cached;
    }
    return cache.computeIfAbsent(key, ignored -> compute(javaType, config));
  }

  private Set<String> compute(JavaType javaType, DeserializationConfig config) {
    Set<String> names = new java.util.HashSet<>();
    ValueDeserializer<?> deserializer =
        mapper._deserializationContext().findNonContextualValueDeserializer(javaType);
    if (!(deserializer instanceof BeanDeserializerBase bean)) {
      return Set.of();
    }
    Class<?> activeView = config.getActiveView();
    for (var creators = bean.creatorProperties(); creators.hasNext(); ) {
      SettableBeanProperty property = creators.next();
      if (!property.isInjectionOnly()
          && (activeView == null || property.visibleInView(activeView))) {
        names.add(property.getName());
      }
    }
    return Set.copyOf(names);
  }

  private static int configHash(DeserializationConfig config) {
    int result = configIdentity(config);
    Class<?> activeView = config.getActiveView();
    return 31 * result + (activeView == null ? 0 : activeView.hashCode());
  }

  private static int configIdentity(MapperConfig<?> config) {
    int result =
        config.getPropertyNamingStrategy() != null
            ? config.getPropertyNamingStrategy().hashCode()
            : 0;
    return 31 * result + config.getDefaultVisibilityChecker().hashCode();
  }

  private record CacheKey(JavaType type, int configHash) {}
}

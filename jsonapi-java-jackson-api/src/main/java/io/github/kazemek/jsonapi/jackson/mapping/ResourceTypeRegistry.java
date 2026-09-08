package io.github.kazemek.jsonapi.jackson.mapping;

import io.github.kazemek.jsonapi.core.validation.MemberNames;
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Immutable, explicit mapping from JSON:API resource types to Java target types. */
public final class ResourceTypeRegistry {

  private final Map<String, Registration> registrations;
  private final List<Registration> orderedRegistrations;

  private ResourceTypeRegistry(Map<String, Registration> registrations) {
    this.registrations = Map.copyOf(new LinkedHashMap<>(registrations));
    this.orderedRegistrations = List.copyOf(registrations.values());
  }

  /** Returns an empty registry builder. */
  public static Builder builder() {
    return new Builder();
  }

  /** Resolves a wire type, or returns {@code null} when it is not registered. */
  public @Nullable Registration resolve(String jsonApiType) {
    return registrations.get(jsonApiType);
  }

  /** Returns registrations in their deterministic insertion order. */
  public List<Registration> registrations() {
    return orderedRegistrations;
  }

  /** One explicit wire-type to Java-type registration. */
  public record Registration(String jsonApiType, Type targetType) {
    public Registration {
      Objects.requireNonNull(jsonApiType, "jsonApiType");
      Objects.requireNonNull(targetType, "targetType");
    }
  }

  /** Builder for one immutable registry. */
  public static final class Builder {
    private final List<Registration> registrations = new ArrayList<>();

    /** Registers a raw Java class under the explicitly supplied wire type. */
    public Builder register(String jsonApiType, Class<?> targetClass) {
      return register(jsonApiType, (Type) targetClass);
    }

    /** Registers any Java reflection type, including parameterized types. */
    public Builder register(String jsonApiType, Type targetType) {
      Objects.requireNonNull(jsonApiType, "jsonApiType");
      Objects.requireNonNull(targetType, "targetType");
      if (!MemberNames.isValid(jsonApiType)) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.INVALID_RESOURCE_TYPE,
            rawClass(targetType),
            "Invalid JSON:API resource type '" + jsonApiType + "'");
      }
      registrations.add(new Registration(jsonApiType, targetType));
      return this;
    }

    /** Builds the registry, rejecting duplicate wire types. */
    public ResourceTypeRegistry build() {
      Map<String, Registration> resolved = new LinkedHashMap<>();
      for (Registration registration : registrations) {
        Registration existing = resolved.putIfAbsent(registration.jsonApiType(), registration);
        if (existing != null) {
          throw JsonApiMappingException.withoutLocation(
              MappingDiagnostic.CONFLICTING_TYPE_REGISTRATION,
              rawClass(registration.targetType()),
              "Conflicting JSON:API type '"
                  + registration.jsonApiType()
                  + "' registered by "
                  + rawClass(existing.targetType()).getName()
                  + " and "
                  + rawClass(registration.targetType()).getName());
        }
      }
      return new ResourceTypeRegistry(resolved);
    }

    private static Class<?> rawClass(Type type) {
      if (type instanceof Class<?> clazz) {
        return clazz;
      }
      if (type instanceof ParameterizedType parameterized
          && parameterized.getRawType() instanceof Class<?> rawClass) {
        return rawClass;
      }
      return Object.class;
    }
  }
}

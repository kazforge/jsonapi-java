package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.DocumentData;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceIdentity;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.mapping.DomainData;
import com.kazforge.jsonapi.mapping.IncludedResources;
import com.kazforge.jsonapi.mapping.ResourceTypeRegistry;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral typed-envelope document binding shared by Jackson adapters.
 *
 * <p>It owns eager registry coherence, primary-data dispatch (including identifier pass-through),
 * included-resource binding with identity aliasing and duplicate-identity rejection,
 * document-prefix composition of binder failures, and {@code /meta} conversion-failure translation.
 * Native target-type construction, configured resource-type name resolution, raw diagnostic class,
 * and per-resource binding stay backend-owned and are reached through {@link Backend}.
 *
 * <p>This type is unsupported implementation detail for backend cooperation, not consumer SPI, and
 * must not appear in supported backend public signatures.
 *
 * @param <T> opaque backend-native type token
 */
@NullMarked
public final class TypedEnvelopeBinder<T> {

  /**
   * Thin native edge required by the shared typed-envelope binder.
   *
   * <p>Only native mechanics live here: constructing a target-type token from the registered
   * reflection {@link Type}, the raw class used in diagnostics, configured resource-type name
   * resolution, and per-resource binding. Native failures surface as the backend's own {@code
   * JsonApiMappingException} diagnostic.
   */
  public interface Backend<T> {

    /** Constructs the backend-native target type for one registered reflection type. */
    T targetType(Type registeredType);

    /** Raw class of a native target type, for registry-coherence diagnostics. */
    Class<?> rawClass(T targetType);

    /**
     * Resolves and validates the configured resource-type name for a native target type.
     *
     * @throws JsonApiMappingException when configured metadata is missing or invalid
     */
    String requireResourceTypeName(T targetType);

    /** Binds one resource object to the registered native target type. */
    Object bindResource(ResourceObject resource, T targetType);
  }

  private record FunctionBackend<T>(
      Function<Type, T> targetTypes,
      Function<T, Class<?>> rawClasses,
      Function<T, String> resourceTypeNames,
      BiFunction<ResourceObject, T, Object> resourceBinder)
      implements Backend<T> {

    @Override
    public T targetType(Type registeredType) {
      return targetTypes.apply(registeredType);
    }

    @Override
    public Class<?> rawClass(T targetType) {
      return rawClasses.apply(targetType);
    }

    @Override
    public String requireResourceTypeName(T targetType) {
      return resourceTypeNames.apply(targetType);
    }

    @Override
    public Object bindResource(ResourceObject resource, T targetType) {
      return resourceBinder.apply(resource, targetType);
    }
  }

  /**
   * Builds a backend from native method references so adapters do not each host a duplicated {@link
   * Backend} class.
   */
  public static <T> Backend<T> backend(
      Function<Type, T> targetType,
      Function<T, Class<?>> rawClass,
      Function<T, String> requireResourceTypeName,
      BiFunction<ResourceObject, T, Object> bindResource) {
    return new FunctionBackend<>(
        Objects.requireNonNull(targetType, "targetType"),
        Objects.requireNonNull(rawClass, "rawClass"),
        Objects.requireNonNull(requireResourceTypeName, "requireResourceTypeName"),
        Objects.requireNonNull(bindResource, "bindResource"));
  }

  private final ResourceTypeRegistry registry;
  private final Backend<T> backend;

  public TypedEnvelopeBinder(ResourceTypeRegistry registry, Backend<T> backend) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.backend = Objects.requireNonNull(backend, "backend");
    requireRegistryCoherence();
  }

  /**
   * Binds an already-validated document into a domain envelope payload; never re-parses or
   * re-validates.
   */
  public TypedEnvelopeComponents bind(JsonApiDocument document) {
    Objects.requireNonNull(document, "document");
    return new TypedEnvelopeComponents(
        bindData(document.data()),
        document.errors(),
        document.meta(),
        document.jsonapi(),
        document.links(),
        bindIncluded(document.included()),
        document.additionalMembers());
  }

  /**
   * Converts document-level meta through the caller-supplied native conversion, or returns {@code
   * null} when {@code meta} is absent. Conversion failures become {@link
   * MappingDiagnostic#UNSUPPORTED_ATTRIBUTE_VALUE} at {@code /meta}.
   */
  public static @Nullable Object convertMeta(
      @Nullable Meta meta, Object targetType, Function<Meta, Object> conversion) {
    if (meta == null) {
      return null;
    }
    try {
      return conversion.apply(meta);
    } catch (RuntimeException ex) {
      throw new JsonApiMappingException(
          MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE,
          null,
          MappingLocation.of("meta"),
          "Failed to convert meta members to " + targetType,
          ex);
    }
  }

  private void requireRegistryCoherence() {
    for (ResourceTypeRegistry.Registration registered : registry.registrations()) {
      T targetType = backend.targetType(registered.targetType());
      String configuredType = backend.requireResourceTypeName(targetType);
      if (!configuredType.equals(registered.jsonApiType())) {
        Class<?> rawClass = backend.rawClass(targetType);
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.RESOURCE_TYPE_MISMATCH,
            rawClass,
            "Registered JSON:API type '"
                + registered.jsonApiType()
                + "' for "
                + rawClass.getName()
                + " does not match configured resource type '"
                + configuredType
                + "'");
      }
    }
  }

  private @Nullable DomainData bindData(@Nullable DocumentData data) {
    if (data == null) {
      return null;
    }
    return switch (data) {
      case DocumentData.NullData() -> DomainData.NullData.INSTANCE;
      case DocumentData.SingleResource(ResourceObject resource) ->
          new DomainData.SingleResource(bindResource(resource, MappingLocation.of("data")));
      case DocumentData.ResourceCollection(List<ResourceObject> resources) -> {
        List<Object> bound = new ArrayList<>(resources.size());
        for (int i = 0; i < resources.size(); i++) {
          bound.add(
              bindResource(resources.get(i), MappingLocation.of("data", Integer.toString(i))));
        }
        yield new DomainData.ResourceCollection(bound);
      }
      case DocumentData.SingleIdentifier(ResourceIdentifier identifier) ->
          new DomainData.SingleIdentifier(identifier);
      case DocumentData.IdentifierCollection(List<ResourceIdentifier> identifiers) ->
          new DomainData.IdentifierCollection(identifiers);
    };
  }

  private @Nullable IncludedResources bindIncluded(@Nullable List<ResourceObject> included) {
    if (included == null) {
      return null;
    }
    List<Object> bound = new ArrayList<>(included.size());
    List<Set<ResourceIdentity>> identitiesByPosition = new ArrayList<>(included.size());
    Set<ResourceIdentity> seen = new LinkedHashSet<>();
    for (int i = 0; i < included.size(); i++) {
      ResourceObject resource = included.get(i);
      MappingLocation pointer = MappingLocation.of("included", Integer.toString(i));
      Object dto = bindResource(resource, pointer);
      bound.add(dto);
      Set<ResourceIdentity> identities = new LinkedHashSet<>();
      if (resource.hasId()) {
        ResourceIdentity identity =
            ResourceIdentity.ofId(resource.type(), Objects.requireNonNull(resource.id()));
        putIdentity(seen, identity, pointer);
        identities.add(identity);
      }
      if (resource.hasLid()) {
        ResourceIdentity identity =
            ResourceIdentity.ofLid(resource.type(), Objects.requireNonNull(resource.lid()));
        putIdentity(seen, identity, pointer);
        identities.add(identity);
      }
      identitiesByPosition.add(identities);
    }
    return IncludedResources.of(bound, identitiesByPosition);
  }

  private static void putIdentity(
      Set<ResourceIdentity> seen, ResourceIdentity identity, MappingLocation pointer) {
    if (!seen.add(identity)) {
      throw new JsonApiMappingException(
          MappingDiagnostic.CONFLICTING_INCLUDED_REPRESENTATION,
          null,
          pointer,
          "Duplicate included identity " + identity);
    }
  }

  /**
   * Binds one resource under the given document-relative prefix. Registry misses fail at the prefix
   * itself; binder failures compose structurally: the document prefix joins the binder's
   * resource-relative location ({@code /data/2} + {@code /attributes/title} = {@code
   * /data/2/attributes/title}), and a binder failure without a location reports just the document
   * prefix rather than inventing a member.
   */
  private Object bindResource(ResourceObject resource, MappingLocation documentPrefix) {
    ResourceObject checkedResource = Objects.requireNonNull(resource, "resource");
    ResourceTypeRegistry.Registration registered = registry.resolve(checkedResource.type());
    if (registered == null) {
      throw new JsonApiMappingException(
          MappingDiagnostic.UNREGISTERED_RESOURCE_TYPE,
          null,
          documentPrefix,
          "No DTO target registered for JSON:API resource type '" + checkedResource.type() + "'");
    }
    T targetType = backend.targetType(registered.targetType());
    try {
      return backend.bindResource(checkedResource, targetType);
    } catch (JsonApiMappingException ex) {
      String message = ex.getMessage() != null ? ex.getMessage() : ex.diagnostic().name();
      MappingLocation relative = ex.location();
      MappingLocation composed =
          relative == null ? documentPrefix : documentPrefix.append(relative);
      throw new JsonApiMappingException(ex.diagnostic(), ex.resourceClass(), composed, message, ex);
    }
  }
}

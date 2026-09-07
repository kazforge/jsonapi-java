package io.github.kazemek.jsonapi.jackson2;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.kazemek.jsonapi.core.model.ResourceObject;
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic;
import io.github.kazemek.jsonapi.jackson2.internal.DomainResourceBinder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Binds validated JSON:API {@link ResourceObject} values to annotated flat DTO types.
 *
 * <p>Construct instances via {@link JsonApiJackson2#resourceBinder(JsonMapper)} or its overloads,
 * never directly. The binder is safe for concurrent use once created. Binding uses the mapping
 * definitions (resolver and cache) and one {@link JsonMapper#convertValue(Object, JavaType)} per
 * resource, so Jackson's logical property model, creators, deserializers, converters, naming,
 * mix-ins, and configured modules remain authoritative (ADR-004). JSON:API annotations assign
 * semantic roles; unannotated Jackson-visible properties do not participate, except the
 * conventional identifier whose Jackson external name is {@code id}. Wire {@code id} binds only to
 * the id role and wire {@code lid} only to the {@code @JsonApiLocalId} role; neither member ever
 * falls back into the other role's property.
 *
 * <p>Binding is read-only and document-first: callers pass an already-validated {@link
 * ResourceObject} and the binder never parses JSON nor reads document {@code included} (ADR-006,
 * ADR-011). Relationship properties receive linkage only — {@link
 * io.github.kazemek.jsonapi.core.model.ResourceIdentifier} (and {@link java.util.Optional}, {@link
 * java.util.List}, {@link java.util.Set}, or array variants) bind from linkage directly; any other
 * target class requires a registered {@link RelationshipLinkageMapper}. Built-in identifier linkage
 * preserves {@code ResourceIdentifier.meta} (ADR-017) and still drops additional members. Write
 * overlay of application-owned identifier meta uses opt-in {@link
 * io.github.kazemek.jsonapi.jackson.mapping.RelationshipLinkage}; it is not relationship-level
 * {@code meta}.
 *
 * <p>Binding failures throw {@link JsonApiMappingException} with a stable {@link MappingDiagnostic}
 * and a resource-relative JSON Pointer-like path.
 */
public final class JsonApiResourceBinder {

  private static final String RESOURCE = "resource";
  private static final String TARGET_TYPE = "targetType";

  private final JsonMapper mapper;
  private final DomainResourceBinder binder;

  JsonApiResourceBinder(JsonMapper mapper, DomainResourceBinder binder) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.binder = Objects.requireNonNull(binder, "binder");
  }

  /** Binds one resource object to the given DTO type. */
  public <T> T fromResource(ResourceObject resource, Class<T> targetType) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(targetType, TARGET_TYPE);
    return targetType.cast(binder.fromResource(resource, mapper.constructType(targetType)));
  }

  /** Binds one resource object to the given DTO Java type. */
  public Object fromResource(ResourceObject resource, JavaType targetType) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(targetType, TARGET_TYPE);
    return binder.fromResource(resource, targetType);
  }

  /**
   * Binds a homogeneous collection of resource objects to DTOs of the given type. Every element's
   * {@code type} must match the target's {@code @JsonApiResource.type()}; heterogeneous lists are
   * out of scope.
   */
  public <T> List<T> fromResources(List<ResourceObject> resources, Class<T> targetType) {
    Objects.requireNonNull(resources, "resources");
    Objects.requireNonNull(targetType, TARGET_TYPE);
    JavaType resolvedType = mapper.constructType(targetType);
    List<T> bound = new ArrayList<>(resources.size());
    for (ResourceObject resource : resources) {
      Objects.requireNonNull(resource, RESOURCE);
      bound.add(targetType.cast(binder.fromResource(resource, resolvedType)));
    }
    return List.copyOf(bound);
  }

  /**
   * Binds a homogeneous collection of resource objects to DTOs of the given Java type. Every
   * element's {@code type} must match the target's {@code @JsonApiResource.type()}.
   */
  public List<Object> fromResources(List<ResourceObject> resources, JavaType targetType) {
    Objects.requireNonNull(resources, "resources");
    Objects.requireNonNull(targetType, TARGET_TYPE);
    List<Object> bound = new ArrayList<>(resources.size());
    for (ResourceObject resource : resources) {
      bound.add(fromResource(resource, targetType));
    }
    return List.copyOf(bound);
  }
}

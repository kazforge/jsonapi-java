package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.JsonApiMembers;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.internal.mapping.IdentifierMetaSupport;
import com.kazforge.jsonapi.mapping.RelationshipLinkage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral relationship-linkage orchestration shared by ordinary flat reads and low-level
 * PATCH binding.
 *
 * <p>It owns cardinality validation, null/empty short-circuiting, direct {@link ResourceIdentifier}
 * copies that preserve identifier meta and drop additional members, opt-in {@code
 * RelationshipLinkage} occurrence orchestration with per-occurrence target/meta pairing, configured
 * linkage-mapper callback sequencing, and the shared cardinality and linkage-mapping diagnostics.
 * Native relationship-shape resolution (target/type resolution plus configured-mapper selection),
 * mapper invocation, and identifier-meta conversion stay backend-owned and are reached lazily
 * through {@link Backend} only after supplied {@code data} is present and bindable.
 *
 * <p>Relationship-shape resolution happens only when the shared binder is called, which the caller
 * does only after it established supplied {@code data}; no mapper is invoked for null or empty
 * linkage.
 *
 * <p>This type is unsupported implementation detail for backend cooperation, not consumer SPI, and
 * must not appear in supported backend public signatures.
 *
 * @param <T> opaque backend-native type token
 * @param <P> opaque backend-native relationship-property token
 */
@NullMarked
public final class RelationshipLinkageBinder<T, P extends RelationshipBindingProperty> {

  /**
   * Thin native edge required by the shared relationship-linkage binder.
   *
   * <p>Only native mechanics live here: the raw diagnostic class of a mapped property, lazy
   * relationship-shape resolution (target/type resolution plus configured-mapper selection),
   * configured linkage-mapper invocation, and declared identifier-meta conversion. Native failures
   * surface as the backend's own {@code JsonApiMappingException} diagnostic.
   */
  public interface Backend<T, P extends RelationshipBindingProperty> {

    /**
     * Raw class of a mapped property's effective target type, for cardinality and linkage
     * diagnostics.
     */
    Class<?> rawType(P property);

    /**
     * Resolves the declared neutral relationship shape of one mapped property. Called lazily only
     * after supplied relationship {@code data} is present, so an unsupported or unresolvable target
     * fails before the shared cardinality and null/empty short-circuit checks.
     */
    ReadRelationshipShape<T> readRelationshipShape(P property);

    /**
     * Invokes the configured linkage mapper for one non-empty, cardinality-valid mapped linkage
     * branch and returns the synthetic property value, exactly once per selected mapped
     * relationship (or once per wrapped to-many occurrence). A null result is returned unchanged:
     * an ordinary to-one mapper result binds a null property, a wrapped to-one null target yields
     * no wrapper, and a wrapped to-many null target is failed by the shared binder at that
     * occurrence's indexed relationship-data location.
     */
    @Nullable Object mapLinkage(P property, RelationshipData data, T target);

    /**
     * Converts one present wrapper occurrence's identifier {@link Meta} to the declared
     * identifier-meta token. The shared binder decides presence, occurrence index, and the
     * identifier-meta location; conversion failures surface as the backend's own identifier-meta
     * diagnostic at that location.
     */
    @Nullable Object convertIdentifierMeta(P property, Meta meta, T metaToken, int occurrenceIndex);
  }

  private final Backend<T, P> backend;

  public RelationshipLinkageBinder(Backend<T, P> backend) {
    this.backend = Objects.requireNonNull(backend, "backend");
  }

  /**
   * Binds one present relationship's linkage. The caller must have established supplied {@code
   * data} and property bindability, so an unsupported or unresolvable target still fails before the
   * shared cardinality and null/empty short-circuit checks and no mapper sees empty linkage.
   */
  public @Nullable Object bind(P property, RelationshipData data) {
    Objects.requireNonNull(property, "property");
    Objects.requireNonNull(data, "data");
    ReadRelationshipShape<T> shape = backend.readRelationshipShape(property);
    if (shape instanceof ReadRelationshipShape.Wrapped<T> wrapped) {
      return bindWrapper(property, wrapped, data);
    }
    boolean empty = validateCardinality(property, data, shape.toMany());
    if (empty) {
      return shape.toMany() ? List.of() : null;
    }
    if (shape instanceof ReadRelationshipShape.Direct<T>) {
      return copyDirectLinkage(data);
    }
    ReadRelationshipShape.Mapped<T> mapped = (ReadRelationshipShape.Mapped<T>) shape;
    return backend.mapLinkage(property, data, mapped.target());
  }

  private @Nullable Object bindWrapper(
      P property, ReadRelationshipShape.Wrapped<T> wrapped, RelationshipData data) {
    return wrapped.toMany()
        ? bindWrappedToMany(property, wrapped, data)
        : bindWrappedToOne(property, wrapped, data);
  }

  private @Nullable Object bindWrappedToOne(
      P property, ReadRelationshipShape.Wrapped<T> wrapped, RelationshipData data) {
    boolean empty = validateCardinality(property, data, false);
    if (empty) {
      return null;
    }
    Object target = mapWrapperTarget(property, wrapped.targetShape(), data);
    if (target == null) {
      return null;
    }
    Object meta = convertOccurrenceMeta(property, wrapped, singleIdentifier(data), -1);
    return new RelationshipLinkage<>(target, meta);
  }

  private Object bindWrappedToMany(
      P property, ReadRelationshipShape.Wrapped<T> wrapped, RelationshipData data) {
    boolean empty = validateCardinality(property, data, true);
    List<ResourceIdentifier> identifiers =
        data instanceof RelationshipData.IdentifierCollectionLinkage(List<ResourceIdentifier> ids)
            ? ids
            : List.of();
    if (empty) {
      return List.of();
    }
    List<Object> values = new ArrayList<>(identifiers.size());
    for (int index = 0; index < identifiers.size(); index++) {
      ResourceIdentifier identifier = identifiers.get(index);
      Object target =
          mapWrapperTarget(
              property, wrapped.targetShape(), new RelationshipData.SingleLinkage(identifier));
      if (target == null) {
        throw linkageMappingFailed(property, index);
      }
      Object meta = convertOccurrenceMeta(property, wrapped, identifier, index);
      values.add(new RelationshipLinkage<>(target, meta));
    }
    return values;
  }

  /**
   * Maps one wrapper occurrence's target through the declared target shape: a direct target copies
   * the identifier (preserving identifier meta and dropping additional members), while a mapped
   * target delegates to the backend's configured mapper with the occurrence's own linkage.
   */
  private @Nullable Object mapWrapperTarget(
      P property, ReadRelationshipShape<T> targetShape, RelationshipData data) {
    if (targetShape instanceof ReadRelationshipShape.Direct<T>) {
      ResourceIdentifier identifier = singleIdentifier(data);
      return identifier == null ? null : IdentifierMetaSupport.copyLinkageIdentifier(identifier);
    }
    ReadRelationshipShape.Mapped<T> mapped = (ReadRelationshipShape.Mapped<T>) targetShape;
    return backend.mapLinkage(property, data, mapped.target());
  }

  private @Nullable Object convertOccurrenceMeta(
      P property,
      ReadRelationshipShape.Wrapped<T> wrapped,
      @Nullable ResourceIdentifier identifier,
      int occurrenceIndex) {
    if (identifier == null || identifier.meta() == null) {
      return null;
    }
    return backend.convertIdentifierMeta(
        property, Objects.requireNonNull(identifier.meta()), wrapped.meta(), occurrenceIndex);
  }

  private static @Nullable ResourceIdentifier singleIdentifier(RelationshipData data) {
    return data instanceof RelationshipData.SingleLinkage(ResourceIdentifier identifier)
        ? identifier
        : null;
  }

  /**
   * Copies one direct relationship linkage, preserving each identifier's type, id, lid, and meta
   * and dropping its additional members. Empty to-many linkage is short-circuited by the caller.
   */
  private static @Nullable Object copyDirectLinkage(RelationshipData data) {
    return switch (data) {
      case RelationshipData.NullLinkage ignored -> null;
      case RelationshipData.SingleLinkage(ResourceIdentifier identifier) ->
          IdentifierMetaSupport.copyLinkageIdentifier(identifier);
      case RelationshipData.IdentifierCollectionLinkage(List<ResourceIdentifier> identifiers) -> {
        List<Object> values = new ArrayList<>(identifiers.size());
        for (ResourceIdentifier identifier : identifiers) {
          values.add(IdentifierMetaSupport.copyLinkageIdentifier(identifier));
        }
        yield values;
      }
    };
  }

  /**
   * Validates linkage shape against the property's declared cardinality, throwing {@link
   * MappingDiagnostic#RELATIONSHIP_CARDINALITY_MISMATCH} for illegal combinations. Returns whether
   * the linkage denotes an empty value ({@code null} on to-one, empty collection on to-many).
   */
  private boolean validateCardinality(P property, RelationshipData data, boolean toMany) {
    return switch (data) {
      case RelationshipData.NullLinkage ignored -> {
        if (toMany) {
          throw cardinalityMismatch(property, "null linkage on to-many relationship");
        }
        yield true;
      }
      case RelationshipData.SingleLinkage ignored -> {
        if (toMany) {
          throw cardinalityMismatch(property, "single linkage on to-many relationship");
        }
        yield false;
      }
      case RelationshipData.IdentifierCollectionLinkage(List<ResourceIdentifier> identifiers) -> {
        boolean empty = identifiers.isEmpty();
        if (!toMany) {
          throw cardinalityMismatch(
              property,
              empty
                  ? "empty collection linkage on to-one relationship"
                  : "collection linkage on to-one relationship");
        }
        yield empty;
      }
    };
  }

  private JsonApiMappingException cardinalityMismatch(P property, String detail) {
    return new JsonApiMappingException(
        MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH,
        backend.rawType(property),
        MappingLocation.of(
            JsonApiMembers.RELATIONSHIPS, property.jsonapiName(), JsonApiMembers.DATA),
        "Cardinality mismatch for relationship '" + property.logicalName() + "': " + detail);
  }

  private JsonApiMappingException linkageMappingFailed(P property, int index) {
    return new JsonApiMappingException(
        MappingDiagnostic.LINKAGE_MAPPING_FAILED,
        backend.rawType(property),
        MappingLocation.of(
            JsonApiMembers.RELATIONSHIPS,
            property.jsonapiName(),
            JsonApiMembers.DATA,
            Integer.toString(index)),
        "Relationship linkage mapper returned null for relationship '"
            + property.logicalName()
            + "'");
  }
}

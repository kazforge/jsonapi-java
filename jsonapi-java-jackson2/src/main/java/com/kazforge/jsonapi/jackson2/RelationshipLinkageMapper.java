package com.kazforge.jsonapi.jackson2;

import com.fasterxml.jackson.databind.JavaType;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.jackson.mapping.IdentifierConverter;
import com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage;
import org.jspecify.annotations.Nullable;

/**
 * Converts JSON:API relationship linkage into a value for a flat DTO relationship property.
 *
 * <p>Register implementations by target class with {@link
 * JsonApiJackson2#resourceBinder(com.fasterxml.jackson.databind.json.JsonMapper,
 * IdentifierConverter, java.util.Map)} when a relationship property's target type is not one of the
 * built-in {@link com.kazforge.jsonapi.core.model.ResourceIdentifier} shapes. The binder invokes
 * the mapper only for {@link RelationshipData.SingleLinkage} (to-one properties, including each
 * occurrence of a to-many {@link RelationshipLinkage} collection) and non-empty {@link
 * RelationshipData.IdentifierCollectionLinkage} (ordinary to-many properties); explicit null and
 * empty linkage short-circuit without a mapper call, and to-one versus to-many cardinality is
 * enforced before invocation.
 *
 * <p>{@code targetType} is {@code T} for to-one properties and for each wrapped to-many occurrence,
 * or the collection type of {@code T} for ordinary to-many properties ({@link java.util.Optional}
 * unwrapped). The returned value is placed directly into the binder's synthetic property map, so it
 * must be coercible to {@code targetType}. A {@code null} return binds a to-one property to {@code
 * null}. For a wrapped to-many occurrence, {@code null} is {@link
 * MappingDiagnostic#LINKAGE_MAPPING_FAILED} : {@link RelationshipLinkage#target()} cannot be null,
 * and omitting the wire identifier would drop a linkage entry. Wrapped to-many properties never
 * reassociate a collection-level mapper result by index.
 */
@FunctionalInterface
public interface RelationshipLinkageMapper {

  /**
   * Converts linkage to a property value, or returns {@code null} for an empty to-one value.
   *
   * <p>A {@code null} return for a wrapped to-many occurrence fails mapping with {@link
   * MappingDiagnostic#LINKAGE_MAPPING_FAILED}.
   *
   * @throws RuntimeException when the linkage cannot be converted; the binder reports this as
   *     {@link MappingDiagnostic#LINKAGE_MAPPING_FAILED}
   */
  @Nullable Object map(RelationshipData linkage, JavaType targetType);
}

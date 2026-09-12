package com.kazforge.jsonapi.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a domain property as the complete JSON:API {@code meta} object of a specific mapped
 * relationship, without requiring a relationship-envelope property. This is relationship-level meta
 * ({@code Relationship.meta}), not per-linkage identifier meta ({@code RelationshipLinkage}).
 *
 * <p>{@link #relationship()} is required and has no default: it identifies the target mapped
 * relationship by its Jackson property identity (the internal Java/Jackson property name, such as a
 * record component or JavaBean property), not the relationship's final JSON:API wire member name.
 * Mapping resolves that identity to the {@link JsonApiRelationship} property on the same domain
 * type, then reads and writes this meta under that relationship's configured-Jackson external name.
 * Renaming the relationship through Jackson therefore carries its relationship meta automatically;
 * callers must not repeat the wire name here.
 *
 * <p>At most one {@code @JsonApiRelationshipMeta} property may target a given relationship, and
 * each such property represents the complete {@code meta} object of that relationship's location.
 * Jackson mapping owns target-shape validation, conversion, and diagnostics. Mapping meta for an
 * otherwise undeclared relationship is not supported.
 *
 * <p>This annotation is not {@link java.lang.annotation.Inherited}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({
  ElementType.FIELD,
  ElementType.METHOD,
  ElementType.PARAMETER,
  ElementType.RECORD_COMPONENT
})
public @interface JsonApiRelationshipMeta {

  /**
   * Jackson property identity of the target mapped relationship.
   *
   * <p>This is the relationship property's internal name ({@code comments} in {@code List<Comment>
   * comments}), not a JSON:API member-name override. Emptiness is validated when a Jackson mapping
   * definition is built; the target must resolve to a {@link JsonApiRelationship} on the same
   * mapping.
   *
   * @return non-empty Jackson property identity of the target relationship
   */
  String relationship();
}

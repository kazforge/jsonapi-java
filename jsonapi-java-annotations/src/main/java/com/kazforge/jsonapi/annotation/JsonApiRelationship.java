package com.kazforge.jsonapi.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a domain property as JSON:API relationship linkage.
 *
 * <p>This annotation assigns the relationship semantic role only. It does not name the member,
 * request inclusion, or carry fetch, cascade, repository, or ORM policy (ADR-005). Configured
 * Jackson is the sole authority for property discovery, visibility, mix-ins, creators,
 * serializers/deserializers, cardinality, value-shape, and the external JSON:API relationship
 * member name ({@code @JsonProperty}, naming strategies, and other Jackson property mechanics).
 *
 * <p>A Jackson-visible property participates as a relationship only when this annotation is
 * present. Annotations never make Jackson-ignored properties visible.
 *
 * <p>Targets include fields, methods, parameters, and record components. Mapping collapses
 * propagated occurrences into one logical property.
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
public @interface JsonApiRelationship {}

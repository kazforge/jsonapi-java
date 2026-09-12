package com.kazforge.jsonapi.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a domain property as a JSON:API attribute.
 *
 * <p>This annotation assigns the attribute semantic role only. It does not name the member:
 * configured Jackson is the sole authority for property discovery, visibility, mix-ins, creators,
 * serializers/deserializers, and the external JSON:API member name ({@code @JsonProperty}, naming
 * strategies, and other Jackson property mechanics).
 *
 * <p>A Jackson-visible property participates as an attribute only when this annotation is present.
 * Otherwise-unclassified properties do not become attributes by fallback. Annotations never make
 * Jackson-ignored properties visible. The conventional property whose configured Jackson external
 * name is {@code id} is the sole intentional implicit JSON:API property-role convention and is
 * owned by {@link JsonApiId}.
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
public @interface JsonApiAttribute {}

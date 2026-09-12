package com.kazforge.jsonapi.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the JSON:API resource identifier ({@code id}) on a domain property.
 *
 * <p>An explicit {@code @JsonApiId}, or a Jackson-visible property whose configured Jackson
 * external name is {@code id}, supplies the identifier. That conventional {@code id} property is
 * the sole intentional implicit JSON:API property-role convention: otherwise-unclassified
 * properties do not participate. Identifier properties never become attributes. Annotations never
 * make Jackson-ignored properties visible; Jackson mapping resolves visibility through Jackson's
 * logical property model and owns identifier conversion and conflict diagnostics.
 *
 * <p>{@code @JsonApiId} maps only the {@code id} member. The local identifier {@code lid} is an
 * independent identity member mapped by {@link JsonApiLocalId}; neither role ever falls back to the
 * other.
 *
 * <p>The JSON:API document member for the identifier remains {@code id}. Configured Jackson may
 * still rename the Java property used to construct or access that identifier; such a rename does
 * not move the identifier on the wire. A property whose Jackson external name is no longer {@code
 * id} must be annotated {@code @JsonApiId} to remain the identifier.
 *
 * <p>Targets include fields, methods, parameters, and record components so Java and Jackson can
 * expose the same logical property. Mapping collapses propagated occurrences into one logical
 * property rather than inventing field/getter precedence.
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
public @interface JsonApiId {}

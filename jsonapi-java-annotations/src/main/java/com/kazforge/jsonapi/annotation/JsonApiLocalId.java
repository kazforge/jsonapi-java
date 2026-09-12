package com.kazforge.jsonapi.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the JSON:API local identifier ({@code lid}) on a domain property.
 *
 * <p>The local identifier is an independent JSON:API identity member, distinct from the resource
 * {@code id} that {@link JsonApiId} maps. {@code @JsonApiId} maps only {@code id};
 * {@code @JsonApiLocalId} maps only {@code lid}. Neither role ever falls back to the other: a wire
 * or mapped {@code lid} is never emitted or bound as {@code id}, and an {@code id} is never emitted
 * or bound as {@code lid}. A domain type may declare an id role, a local-id role, or both; each
 * role must appear on at most one logical property, and one logical property must not claim both
 * roles.
 *
 * <p>Mapping resolves the annotated property through configured Jackson's logical property model,
 * exactly like every other role: configured Jackson owns discovery, visibility, external naming,
 * mix-ins, creators, and value conversion. The wire member remains {@code lid} even when Jackson
 * renames the Java property. The local identifier carries no naming or conversion policy of its
 * own; identifier conversion is shared with {@code id} through the mapping layer's {@code
 * IdentifierConverter}.
 *
 * <p>The local identifier is a JSON:API protocol concept for resources identified only within their
 * document, such as client-generated identifiers in creation requests. It is not an application
 * persistence or transient-entity heuristic, and mapping never infers id or lid semantics from a
 * value's shape, nullness, or database state.
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
public @interface JsonApiLocalId {}

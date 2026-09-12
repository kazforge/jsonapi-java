package com.kazforge.jsonapi.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a domain type maps to a JSON:API resource of the given {@link #type()}.
 *
 * <p>{@code type()} is required and has no default. It is explicit JSON:API semantic data — the
 * resource {@code type} member — not a Jackson property name. Member-name grammar validation for
 * the type string is performed when a Jackson mapping definition is built, not by this annotation
 * artifact.
 *
 * <p>{@code ElementType.TYPE} permits classes, records, interfaces, enums, and annotation types at
 * compile time. Supported domain shapes and diagnostics for unsupported placements are defined by
 * Jackson domain mapping.
 *
 * <p>This annotation is not {@link java.lang.annotation.Inherited}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JsonApiResource {

  /**
   * JSON:API resource type member value.
   *
   * @return non-empty type string; emptiness and member-name grammar are validated when a Jackson
   *     mapping definition is built
   */
  String type();
}

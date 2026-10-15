package com.kazforge.jsonapi.document;

import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import org.jspecify.annotations.Nullable;

/**
 * Optional top-level links, meta, and JSON:API object attached during domain mapping.
 *
 * <p>A {@code null} component means that member is absent and is omitted from the serialized
 * document. A non-null empty value remains present: for example, {@link Links#empty()} and {@link
 * Meta#empty()} produce empty {@code links} and {@code meta} objects rather than absence.
 */
public record DocumentEnvelope(
    @Nullable Links links, @Nullable Meta meta, @Nullable JsonApiObject jsonapi) {

  /** Returns an envelope with every member absent, not present-empty. */
  public static DocumentEnvelope empty() {
    return new DocumentEnvelope(null, null, null);
  }

  /** Returns an envelope containing only links, or no members when {@code null}. */
  public static DocumentEnvelope ofLinks(@Nullable Links links) {
    return new DocumentEnvelope(links, null, null);
  }

  /** Returns an envelope containing only metadata, or no members when {@code null}. */
  public static DocumentEnvelope ofMeta(@Nullable Meta meta) {
    return new DocumentEnvelope(null, meta, null);
  }

  /**
   * Returns an envelope containing only a {@code jsonapi} object, or no members when {@code null}.
   */
  public static DocumentEnvelope ofJsonApi(@Nullable JsonApiObject jsonapi) {
    return new DocumentEnvelope(null, null, jsonapi);
  }

  /** Returns a copy replacing links, or omitting them when {@code null}. */
  public DocumentEnvelope withLinks(@Nullable Links links) {
    return new DocumentEnvelope(links, meta, jsonapi);
  }

  /** Returns a copy replacing metadata, or omitting it when {@code null}. */
  public DocumentEnvelope withMeta(@Nullable Meta meta) {
    return new DocumentEnvelope(links, meta, jsonapi);
  }

  /** Returns a copy replacing the {@code jsonapi} object, or omitting it when {@code null}. */
  public DocumentEnvelope withJsonApi(@Nullable JsonApiObject jsonapi) {
    return new DocumentEnvelope(links, meta, jsonapi);
  }
}

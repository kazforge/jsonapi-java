package com.kazforge.jsonapi.jackson.api;

import com.kazforge.jsonapi.jackson.document.DocumentEnvelope;
import com.kazforge.jsonapi.jackson.representation.RepresentationSelection;
import java.util.Objects;

/**
 * Ordinary resource-write options composing existing neutral semantics.
 *
 * <p>Carries the per-write document envelope (top-level links, meta, and JSON:API object) together
 * with the per-operation representation selection (include paths and sparse fieldsets). An absent
 * per-write {@code jsonapi} member (a null component on the envelope) is distinct from an explicit
 * per-write {@link com.kazforge.jsonapi.core.model.JsonApiObject}: explicit values override future
 * application-lifetime document defaults, while absent values leave those defaults in effect.
 *
 * <p>Representation policy is application/runtime configuration owned by the major-specific
 * runtime, not a per-write value: these options deliberately carry no policy, so a default write
 * always inherits the runtime's effective policy instead of overriding it with a concrete default.
 * Per-call policy overrides remain advanced.
 *
 * <p>Use {@link #defaults()} for the documented ordinary behavior: no document-level members and no
 * inclusion or fieldsets.
 */
public record ResourceWriteOptions(DocumentEnvelope envelope, RepresentationSelection selection) {

  public ResourceWriteOptions {
    Objects.requireNonNull(envelope, "envelope");
    Objects.requireNonNull(selection, "selection");
  }

  /** Returns options with no envelope members and no selection. */
  public static ResourceWriteOptions defaults() {
    return new ResourceWriteOptions(
        new DocumentEnvelope(null, null, null), RepresentationSelection.none());
  }

  /** Returns options with the given envelope and this selection. */
  public ResourceWriteOptions withEnvelope(DocumentEnvelope envelope) {
    return new ResourceWriteOptions(envelope, selection);
  }

  /** Returns options with the given selection and this envelope. */
  public ResourceWriteOptions withSelection(RepresentationSelection selection) {
    return new ResourceWriteOptions(envelope, selection);
  }
}

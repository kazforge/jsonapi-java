package com.kazforge.jsonapi.api;

import com.kazforge.jsonapi.document.DocumentEnvelope;
import com.kazforge.jsonapi.representation.RepresentationSelection;
import java.util.Objects;

/**
 * Backend-independent ordinary resource-write options composing existing neutral semantics. The
 * current Jackson adapters interpret these values through caller-configured Jackson.
 *
 * <p>Carries the per-write document envelope (top-level links, meta, and JSON:API object) together
 * with the per-operation representation selection (include paths and sparse fieldsets). An absent
 * per-write {@code jsonapi} member (a null component on the envelope) is distinct from an explicit
 * per-write {@link com.kazforge.jsonapi.core.model.JsonApiObject}: explicit values override the
 * configured application-lifetime document default, while absence leaves that default in effect.
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

package com.kazforge.jsonapi.api;

import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.patch.PatchCommand;
import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * Level-1 presence-aware PATCH operations.
 *
 * <p>Typed {@code PatchPresence<T>} DTO binding ({@code readPatch} / {@code bindPatch}) is the
 * conventional path for ordinary callers: omitted members, explicit JSON {@code null} / null
 * linkage, and supplied values stay distinct in the bound DTO. {@link PatchCommand} ({@code
 * readCommand} / {@code bindCommand}) remains the explicit lower-level, generic, and infrastructure
 * projection of the same validated update document. Neither path depends on global resource-type
 * registration and neither reads {@code included}.
 *
 * <p>Both paths describe requested changes only. They do not authorize a request, load domain
 * state, resolve conflicts, or apply mutations; applications own those decisions.
 */
public interface JsonApiPatches {

  /**
   * Decodes, validates as an update request, and binds the document directly into the caller's
   * PATCH DTO.
   */
  <T> T readPatch(String json, Class<T> dtoType);

  /** Stream variant of {@link #readPatch(String, Class)}. The stream is not closed. */
  <T> T readPatch(InputStream json, Class<T> dtoType);

  /**
   * Decodes, validates as an update request, and binds the document into a PATCH DTO described by a
   * full generic type. The caller ensures {@code dtoType} denotes the returned value.
   */
  Object readPatch(String json, Type dtoType);

  /**
   * Stream variant of {@link #readPatch(String, Type)}. The stream is not closed. The caller
   * ensures {@code dtoType} denotes the returned value.
   */
  Object readPatch(InputStream json, Type dtoType);

  /**
   * Decodes, validates as an update request, and binds only the supplied changes into a {@link
   * PatchCommand}.
   */
  <T> PatchCommand<T> readCommand(String json, Class<T> resourceType);

  /** Stream variant of {@link #readCommand(String, Class)}. The stream is not closed. */
  <T> PatchCommand<T> readCommand(InputStream json, Class<T> resourceType);

  /**
   * Decodes, validates as an update request, and binds only the supplied changes into a {@link
   * PatchCommand} described by a full generic type.
   *
   * <p>The wildcard is the honest declaration: a plain {@code Type} cannot recover the type
   * argument, so callers assign or narrow the command themselves.
   */
  @SuppressWarnings("java:S1452")
  PatchCommand<?> readCommand(String json, Type resourceType);

  /**
   * Stream variant of {@link #readCommand(String, Type)}. The stream is not closed.
   *
   * <p>The wildcard is the honest declaration: a plain {@code Type} cannot recover the type
   * argument, so callers assign or narrow the command themselves.
   */
  @SuppressWarnings("java:S1452")
  PatchCommand<?> readCommand(InputStream json, Type resourceType);

  /**
   * Binds an already-validated update document into the caller's PATCH DTO without re-parsing or
   * re-validating.
   */
  <T> T bindPatch(JsonApiDocument document, Class<T> dtoType);

  /**
   * Binds an already-validated update document into a PATCH DTO described by a full generic type
   * without re-parsing or re-validating. The caller ensures {@code dtoType} denotes the returned
   * value.
   */
  Object bindPatch(JsonApiDocument document, Type dtoType);

  /**
   * Binds an already-validated update document into a {@link PatchCommand} without re-parsing or
   * re-validating.
   */
  <T> PatchCommand<T> bindCommand(JsonApiDocument document, Class<T> resourceType);

  /**
   * Binds an already-validated update document into a {@link PatchCommand} described by a full
   * generic type without re-parsing or re-validating.
   *
   * <p>The wildcard is the honest declaration: a plain {@code Type} cannot recover the type
   * argument, so callers assign or narrow the command themselves.
   */
  @SuppressWarnings("java:S1452")
  PatchCommand<?> bindCommand(JsonApiDocument document, Type resourceType);
}

package com.kazforge.jsonapi.jackson.api;

import com.kazforge.jsonapi.core.validation.EndpointIdentity;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Level-1 ordinary resource operations: strict homogeneous reads, single/collection writes, and
 * create/update document authoring.
 *
 * <p>Reads bind directly to the caller-supplied target type and never require a resource-type
 * registry. {@code readOne} requires primary data that is exactly one resource object; {@code
 * readMany} requires a resource collection. Null, absent, identifier, error, and cross-shape
 * document states are never silently coerced. The {@code Class} overloads cover ordinary DTOs; the
 * {@code Type} overloads cover generic targets whose fidelity genuinely requires a full type.
 * Generic typed-document envelopes and heterogeneous binding remain advanced.
 *
 * <p>Writes coordinate mapping, configured decoration, mapped-document validation, and document
 * emission internally. {@code writeCreateDocument} selects core {@code CREATE_REQUEST} validation
 * and {@code writeUpdateDocument} selects core {@code UPDATE_REQUEST} validation; neither
 * reimplements core rules. Where the caller has expected-identity context for an update, it is
 * supplied explicitly as an {@link EndpointIdentity}. Operation names express JSON:API semantics
 * and encode no HTTP transport.
 *
 * <p>Configured Jackson remains the sole property-naming authority; {@code id} and {@code lid} stay
 * independent roles; ordinary relationships stay linkage-oriented with a present {@code data}
 * member. Failures reuse the existing diagnostic families; no facade-specific exception type is
 * introduced.
 */
public interface JsonApiResources {

  /**
   * Reads exactly one resource DTO. Requires single-resource primary data.
   *
   * @param <T> the target DTO type
   */
  <T> T readOne(String json, Class<T> type);

  /**
   * Stream variant of {@link #readOne(String, Class)}. The stream is not closed.
   *
   * @param <T> the target DTO type
   */
  <T> T readOne(InputStream json, Class<T> type);

  /**
   * Reads exactly one resource DTO using a full generic type. The caller ensures {@code type}
   * denotes {@code T}.
   */
  Object readOne(String json, Type type);

  /**
   * Stream variant of {@link #readOne(String, Type)}. The stream is not closed. The caller ensures
   * {@code type} denotes the returned value.
   */
  Object readOne(InputStream json, Type type);

  /**
   * Reads a resource collection. Requires collection primary data; never coerces one resource,
   * null, identifiers, or absent data into a collection.
   *
   * @param <T> the target DTO element type
   */
  <T> List<T> readMany(String json, Class<T> type);

  /**
   * Stream variant of {@link #readMany(String, Class)}. The stream is not closed.
   *
   * @param <T> the target DTO element type
   */
  <T> List<T> readMany(InputStream json, Class<T> type);

  /**
   * Reads a resource collection using a full generic element type. The caller ensures {@code type}
   * denotes the collection element type.
   */
  List<Object> readMany(String json, Type type);

  /**
   * Stream variant of {@link #readMany(String, Type)}. The stream is not closed. The caller ensures
   * {@code type} denotes the collection element type.
   */
  List<Object> readMany(InputStream json, Type type);

  /**
   * Reads one resource DTO together with top-level document state. Requires single-resource primary
   * data.
   *
   * @param <T> the target DTO type
   */
  <T> ResourceDocument<T> readOneDocument(String json, Class<T> type);

  /**
   * Stream variant of {@link #readOneDocument(String, Class)}. The stream is not closed.
   *
   * @param <T> the target DTO type
   */
  <T> ResourceDocument<T> readOneDocument(InputStream json, Class<T> type);

  /**
   * Reads a resource collection together with top-level document state. Requires collection primary
   * data.
   *
   * @param <T> the target DTO element type
   */
  <T> ResourceCollectionDocument<T> readManyDocument(String json, Class<T> type);

  /**
   * Stream variant of {@link #readManyDocument(String, Class)}. The stream is not closed.
   *
   * @param <T> the target DTO element type
   */
  <T> ResourceCollectionDocument<T> readManyDocument(InputStream json, Class<T> type);

  /** Writes one resource with default options and returns the JSON document. */
  String writeOne(Object resource);

  /** Writes one resource with the given options and returns the JSON document. */
  String writeOne(Object resource, ResourceWriteOptions options);

  /** Writes one resource with default options to the given stream. The stream is not closed. */
  void writeOne(Object resource, OutputStream out);

  /** Writes one resource with the given options to the given stream. The stream is not closed. */
  void writeOne(Object resource, ResourceWriteOptions options, OutputStream out);

  /** Writes a resource collection with default options and returns the JSON document. */
  String writeMany(Iterable<?> resources);

  /** Writes a resource collection with the given options and returns the JSON document. */
  String writeMany(Iterable<?> resources, ResourceWriteOptions options);

  /**
   * Writes a resource collection with default options to the given stream. The stream is not
   * closed.
   */
  void writeMany(Iterable<?> resources, OutputStream out);

  /**
   * Writes a resource collection with the given options to the given stream. The stream is not
   * closed.
   */
  void writeMany(Iterable<?> resources, ResourceWriteOptions options, OutputStream out);

  /**
   * Authors a create-request document for one resource with default options and returns its JSON.
   * Selects core {@code CREATE_REQUEST} validation.
   */
  String writeCreateDocument(Object resource);

  /**
   * Authors a create-request document for one resource with the given options and returns its JSON.
   * Selects core {@code CREATE_REQUEST} validation.
   */
  String writeCreateDocument(Object resource, ResourceWriteOptions options);

  /**
   * Authors a create-request document for one resource with default options to the given stream.
   * The stream is not closed. Selects core {@code CREATE_REQUEST} validation.
   */
  void writeCreateDocument(Object resource, OutputStream out);

  /**
   * Authors a create-request document for one resource with the given options to the given stream.
   * The stream is not closed. Selects core {@code CREATE_REQUEST} validation.
   */
  void writeCreateDocument(Object resource, ResourceWriteOptions options, OutputStream out);

  /**
   * Authors an update-request document for one resource with default options and returns its JSON.
   * Selects core {@code UPDATE_REQUEST} validation. A null {@code expectedIdentity} disables the
   * endpoint-identity comparison.
   */
  String writeUpdateDocument(Object resource, @Nullable EndpointIdentity expectedIdentity);

  /**
   * Authors an update-request document for one resource with the given options and returns its
   * JSON. Selects core {@code UPDATE_REQUEST} validation. A null {@code expectedIdentity} disables
   * the endpoint-identity comparison.
   */
  String writeUpdateDocument(
      Object resource, @Nullable EndpointIdentity expectedIdentity, ResourceWriteOptions options);

  /**
   * Authors an update-request document for one resource with default options to the given stream.
   * The stream is not closed. Selects core {@code UPDATE_REQUEST} validation. A null {@code
   * expectedIdentity} disables the endpoint-identity comparison.
   */
  void writeUpdateDocument(
      Object resource, @Nullable EndpointIdentity expectedIdentity, OutputStream out);

  /**
   * Authors an update-request document for one resource with the given options to the given stream.
   * The stream is not closed. Selects core {@code UPDATE_REQUEST} validation. A null {@code
   * expectedIdentity} disables the endpoint-identity comparison.
   */
  void writeUpdateDocument(
      Object resource,
      @Nullable EndpointIdentity expectedIdentity,
      ResourceWriteOptions options,
      OutputStream out);
}

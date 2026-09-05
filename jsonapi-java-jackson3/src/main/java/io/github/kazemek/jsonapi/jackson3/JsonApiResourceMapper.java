package io.github.kazemek.jsonapi.jackson3;

import io.github.kazemek.jsonapi.core.model.DocumentData;
import io.github.kazemek.jsonapi.core.model.JsonApiDocument;
import io.github.kazemek.jsonapi.core.model.ResourceObject;
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic;
import io.github.kazemek.jsonapi.jackson.document.DocumentEnvelope;
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter;
import io.github.kazemek.jsonapi.jackson.mapping.MappedDocument;
import io.github.kazemek.jsonapi.jackson.representation.RepresentationPolicy;
import io.github.kazemek.jsonapi.jackson.representation.RepresentationSelection;
import io.github.kazemek.jsonapi.jackson3.internal.CompoundInclusionEngine;
import io.github.kazemek.jsonapi.jackson3.internal.DomainResourceWriter;
import io.github.kazemek.jsonapi.jackson3.internal.EffectiveRepresentation;
import io.github.kazemek.jsonapi.jackson3.internal.IncludedResourcesResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;

/**
 * Maps annotated domain objects to JSON:API {@link ResourceObject} instances and documents.
 *
 * <p>Construct instances via {@link
 * JsonApiJackson3#resourceMapper(tools.jackson.databind.json.JsonMapper)} or its overloads, never
 * directly. The mapper is safe for concurrent use once created. Decorators supplied through a
 * {@link io.github.kazemek.jsonapi.jackson.mapping.ResourceDecoratorRegistry} are invoked during
 * mapping and may be called concurrently; they must themselves be safe for concurrent invocation
 * when the mapper is shared. Mapping uses Jackson's logical property model and caches resolved
 * definitions by type and configuration identity. JSON:API annotations assign semantic roles;
 * configured Jackson owns property discovery, visibility, external naming, mix-ins, creators, and
 * value conversion. Unannotated Jackson-visible properties do not participate, except the
 * conventional identifier whose Jackson external name is {@code id}. {@code @JsonApiId} maps only
 * {@link ResourceObject#id()} and {@code @JsonApiLocalId} maps only {@link ResourceObject#lid()};
 * the two identity roles never fall back to each other.
 *
 * <p>Mapping is write-only: this mapper produces core model objects. Feed them to a {@link
 * JsonApiDocumentWriter} for serialization. Read-side flat DTO binding is provided by {@link
 * JsonApiResourceBinder}.
 *
 * <p>The convenience methods infer a root {@link JavaType} from the concrete runtime class. Use the
 * overloads that accept a {@link JavaType} for a directly parameterized root such as {@code
 * Container<Thing>}; the declared type is retained through mapping, relationship linkage, and
 * compound inclusion. Concrete subclasses that bind a generic superclass can use the convenience
 * methods when Jackson resolves those bindings. An unparameterized generic root fails at the mapped
 * member that requires the missing declaration rather than inferring a type from runtime contents.
 *
 * <p>Compound inclusion is opt-in via {@link RepresentationSelection} and {@link
 * RepresentationPolicy} on the mapper overloads that accept representation inputs. Relationship
 * mapping alone never requests inclusion. Callers without an envelope pass {@code null} for the
 * envelope argument.
 *
 * <p>Sparse fieldsets are applied only by {@link #toMappedDocument(Object, DocumentEnvelope,
 * RepresentationSelection, RepresentationPolicy)} and {@link #toMappedCollectionDocument(Iterable,
 * DocumentEnvelope, RepresentationSelection, RepresentationPolicy)}. Those overloads return a
 * {@link MappedDocument} carrying the identities of included resources whose inbound linkage was
 * removed by an applied fieldset; a document writer composes that provenance into validation. The
 * non-mapped representation overloads (with or without a declared {@link JavaType}) reject a
 * non-empty fieldset map with {@link MappingDiagnostic#FIELDSETS_REQUIRE_MAPPED_DOCUMENT}.
 *
 * <p>For custom identifier conversion, supply an {@link IdentifierConverter} at construction time.
 * The default converter delegates to {@link Object#toString()}.
 */
public final class JsonApiResourceMapper {

  private static final String SELECTION = "selection";
  private static final String POLICY = "policy";
  private static final String RESOURCES = "resources";
  private static final String RESOURCE = "resource";
  private static final String RESOURCE_TYPE = "resourceType";

  private final DomainResourceWriter writer;
  private final CompoundInclusionEngine inclusionEngine;

  JsonApiResourceMapper(DomainResourceWriter writer) {
    this.writer = Objects.requireNonNull(writer, "writer");
    this.inclusionEngine = new CompoundInclusionEngine(writer);
  }

  public ResourceObject toResource(Object resource) {
    return writer.toResource(resource, writer.inferredType(resource));
  }

  /** Maps a domain object using the complete declared Jackson type supplied by the caller. */
  public ResourceObject toResource(Object resource, JavaType resourceType) {
    return writer.toResource(resource, resourceType);
  }

  public JsonApiDocument toDocument(Object resource) {
    return toDocument(resource, null);
  }

  public JsonApiDocument toDocument(Object resource, @Nullable DocumentEnvelope envelope) {
    JavaType resourceType = writer.inferredType(resource);
    return toDocument(resource, resourceType, envelope);
  }

  /** Maps a domain object to a document using a complete declared type and optional envelope. */
  public JsonApiDocument toDocument(
      Object resource, JavaType resourceType, @Nullable DocumentEnvelope envelope) {
    ResourceObject resourceObject = writer.toResource(resource, resourceType);
    return buildDocument(new DocumentData.SingleResource(resourceObject), envelope, null);
  }

  /**
   * Maps a domain object to a document with optional envelope members and explicit compound
   * inclusion from {@code selection} governed by {@code policy}. Rejects a non-empty fieldset map;
   * use {@link #toMappedDocument} when applying sparse fieldsets.
   */
  public JsonApiDocument toDocument(
      Object resource,
      @Nullable DocumentEnvelope envelope,
      RepresentationSelection selection,
      RepresentationPolicy policy) {
    return toDocument(resource, writer.inferredType(resource), envelope, selection, policy);
  }

  /**
   * Maps a domain object to a document with explicit inclusion using the complete declared Jackson
   * type supplied by the caller.
   */
  public JsonApiDocument toDocument(
      Object resource,
      JavaType resourceType,
      @Nullable DocumentEnvelope envelope,
      RepresentationSelection selection,
      RepresentationPolicy policy) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    EffectiveRepresentation representation = effectiveRepresentation(selection, policy);
    rejectNonEmptyFieldsets(representation);
    List<Object> snapshot = List.of(resource);
    ResourceObject resourceObject = writer.toResource(resource, resourceType, representation);
    List<ResourceObject> primary = List.of(resourceObject);
    List<ResourceObject> included =
        inclusionEngine
            .collectIncluded(snapshot, List.of(resourceType), primary, null, representation)
            .included();
    return buildDocument(new DocumentData.SingleResource(resourceObject), envelope, included);
  }

  /**
   * Maps a domain object with optional envelope members, compound inclusion, and sparse fieldsets
   * from {@code selection} governed by {@code policy}. Returns a {@link MappedDocument} whose
   * linkage exemptions name included resources whose linking relationship was omitted by an applied
   * fieldset while inclusion still traversed it.
   */
  public MappedDocument toMappedDocument(
      Object resource,
      @Nullable DocumentEnvelope envelope,
      RepresentationSelection selection,
      RepresentationPolicy policy) {
    return toMappedDocument(resource, writer.inferredType(resource), envelope, selection, policy);
  }

  /** Maps a domain object with inclusion and sparse fieldsets using a complete declared type. */
  public MappedDocument toMappedDocument(
      Object resource,
      JavaType resourceType,
      @Nullable DocumentEnvelope envelope,
      RepresentationSelection selection,
      RepresentationPolicy policy) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    EffectiveRepresentation representation = effectiveRepresentation(selection, policy);
    List<Object> snapshot = List.of(resource);
    ResourceObject resourceObject = writer.toResource(resource, resourceType, representation);
    List<ResourceObject> primary = List.of(resourceObject);
    IncludedResourcesResult includedResult =
        inclusionEngine.collectIncluded(
            snapshot, List.of(resourceType), primary, null, representation);
    JsonApiDocument document =
        buildDocument(
            new DocumentData.SingleResource(resourceObject), envelope, includedResult.included());
    return new MappedDocument(document, includedResult.sparseFieldsetLinkageExemptions());
  }

  /**
   * Maps a domain object for create-request authoring with optional envelope members, compound
   * inclusion, and sparse fieldsets from {@code selection} governed by {@code policy}. Unlike
   * {@link #toMappedDocument}, a primary resource with neither {@code id} nor {@code lid} value
   * maps with both members absent instead of failing; core {@code CREATE_REQUEST} validation owns
   * that leniency when the document is written. Compound inclusion likewise traverses an
   * identity-less primary while included resources still require identity, and related linkage
   * extraction is unchanged. Returns a {@link MappedDocument} carrying sparse-fieldset linkage
   * provenance like {@link #toMappedDocument}.
   */
  public MappedDocument toMappedCreateDocument(
      Object resource,
      @Nullable DocumentEnvelope envelope,
      RepresentationSelection selection,
      RepresentationPolicy policy) {
    return toMappedCreateDocument(
        resource, writer.inferredType(resource), envelope, selection, policy);
  }

  /** Maps a domain object for create-request authoring using a complete declared type. */
  public MappedDocument toMappedCreateDocument(
      Object resource,
      JavaType resourceType,
      @Nullable DocumentEnvelope envelope,
      RepresentationSelection selection,
      RepresentationPolicy policy) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    EffectiveRepresentation representation = effectiveRepresentation(selection, policy);
    List<Object> snapshot = List.of(resource);
    ResourceObject resourceObject = writer.toCreateResource(resource, resourceType, representation);
    List<ResourceObject> primary = List.of(resourceObject);
    IncludedResourcesResult includedResult =
        inclusionEngine.collectIncluded(
            snapshot, List.of(resourceType), primary, null, representation, true);
    JsonApiDocument document =
        buildDocument(
            new DocumentData.SingleResource(resourceObject), envelope, includedResult.included());
    return new MappedDocument(document, includedResult.sparseFieldsetLinkageExemptions());
  }

  public JsonApiDocument toCollectionDocument(Iterable<?> resources) {
    return toCollectionDocument(resources, null);
  }

  public JsonApiDocument toCollectionDocument(
      Iterable<?> resources, @Nullable DocumentEnvelope envelope) {
    Objects.requireNonNull(resources, RESOURCES);
    List<Object> snapshot = materialize(resources);
    List<JavaType> resourceTypes = inferredTypes(snapshot);
    return toCollectionDocument(snapshot, resourceTypes, envelope);
  }

  /** Maps a homogeneous collection using a complete declared element type and optional envelope. */
  public JsonApiDocument toCollectionDocument(
      Iterable<?> resources, JavaType resourceType, @Nullable DocumentEnvelope envelope) {
    Objects.requireNonNull(resources, RESOURCES);
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    List<Object> snapshot = materialize(resources);
    return toCollectionDocument(
        snapshot, effectiveTypes(snapshot, repeatedType(snapshot.size(), resourceType)), envelope);
  }

  private JsonApiDocument toCollectionDocument(
      List<Object> snapshot, List<JavaType> resourceTypes, @Nullable DocumentEnvelope envelope) {
    List<ResourceObject> resourceObjects = new ArrayList<>(snapshot.size());
    for (int i = 0; i < snapshot.size(); i++) {
      resourceObjects.add(writer.toResource(snapshot.get(i), resourceTypes.get(i)));
    }
    return buildDocument(
        new DocumentData.ResourceCollection(List.copyOf(resourceObjects)), envelope, null);
  }

  /**
   * Maps a primary collection to a document with optional envelope members and explicit compound
   * inclusion from {@code selection} governed by {@code policy}. The iterable is materialized once
   * and reused for type validation, primary mapping, and inclusion traversal. Rejects a non-empty
   * fieldset map; use {@link #toMappedCollectionDocument} when applying sparse fieldsets.
   */
  public JsonApiDocument toCollectionDocument(
      Iterable<?> resources,
      @Nullable DocumentEnvelope envelope,
      RepresentationSelection selection,
      RepresentationPolicy policy) {
    Objects.requireNonNull(resources, RESOURCES);
    List<Object> snapshot = materialize(resources);
    return toCollectionDocument(
        snapshot,
        inferredTypes(snapshot),
        envelope,
        effectiveRepresentation(selection, policy),
        null);
  }

  /**
   * Maps a homogeneous collection with explicit compound inclusion using the complete declared
   * element type supplied by the caller.
   */
  public JsonApiDocument toCollectionDocument(
      Iterable<?> resources,
      JavaType resourceType,
      @Nullable DocumentEnvelope envelope,
      RepresentationSelection selection,
      RepresentationPolicy policy) {
    Objects.requireNonNull(resources, RESOURCES);
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    List<Object> snapshot = materialize(resources);
    return toCollectionDocument(
        snapshot,
        effectiveTypes(snapshot, repeatedType(snapshot.size(), resourceType)),
        envelope,
        effectiveRepresentation(selection, policy),
        resourceType);
  }

  private JsonApiDocument toCollectionDocument(
      List<Object> snapshot,
      List<JavaType> resourceTypes,
      @Nullable DocumentEnvelope envelope,
      EffectiveRepresentation representation,
      @Nullable JavaType emptyCollectionType) {
    rejectNonEmptyFieldsets(representation);
    List<ResourceObject> resourceObjects = new ArrayList<>(snapshot.size());
    for (int i = 0; i < snapshot.size(); i++) {
      resourceObjects.add(writer.toResource(snapshot.get(i), resourceTypes.get(i), representation));
    }
    List<ResourceObject> primary = List.copyOf(resourceObjects);
    List<ResourceObject> included =
        inclusionEngine
            .collectIncluded(snapshot, resourceTypes, primary, emptyCollectionType, representation)
            .included();
    return buildDocument(new DocumentData.ResourceCollection(primary), envelope, included);
  }

  /**
   * Maps a primary collection with optional envelope members, compound inclusion, and sparse
   * fieldsets from {@code selection} governed by {@code policy}. The iterable is materialized once.
   * Returns a {@link MappedDocument} whose linkage exemptions fold fieldset-removed linking
   * relationships across primary and included selective writes.
   */
  public MappedDocument toMappedCollectionDocument(
      Iterable<?> resources,
      @Nullable DocumentEnvelope envelope,
      RepresentationSelection selection,
      RepresentationPolicy policy) {
    Objects.requireNonNull(resources, RESOURCES);
    List<Object> snapshot = materialize(resources);
    return toMappedCollectionDocument(
        snapshot,
        inferredTypes(snapshot),
        envelope,
        effectiveRepresentation(selection, policy),
        null);
  }

  /**
   * Maps a homogeneous collection with inclusion and sparse fieldsets using a complete declared
   * type.
   */
  public MappedDocument toMappedCollectionDocument(
      Iterable<?> resources,
      JavaType resourceType,
      @Nullable DocumentEnvelope envelope,
      RepresentationSelection selection,
      RepresentationPolicy policy) {
    Objects.requireNonNull(resources, RESOURCES);
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    List<Object> snapshot = materialize(resources);
    return toMappedCollectionDocument(
        snapshot,
        effectiveTypes(snapshot, repeatedType(snapshot.size(), resourceType)),
        envelope,
        effectiveRepresentation(selection, policy),
        resourceType);
  }

  private MappedDocument toMappedCollectionDocument(
      List<Object> snapshot,
      List<JavaType> resourceTypes,
      @Nullable DocumentEnvelope envelope,
      EffectiveRepresentation representation,
      @Nullable JavaType emptyCollectionType) {
    List<ResourceObject> resourceObjects = new ArrayList<>(snapshot.size());
    for (int i = 0; i < snapshot.size(); i++) {
      resourceObjects.add(writer.toResource(snapshot.get(i), resourceTypes.get(i), representation));
    }
    List<ResourceObject> primary = List.copyOf(resourceObjects);
    IncludedResourcesResult includedResult =
        inclusionEngine.collectIncluded(
            snapshot, resourceTypes, primary, emptyCollectionType, representation);
    JsonApiDocument document =
        buildDocument(
            new DocumentData.ResourceCollection(primary), envelope, includedResult.included());
    return new MappedDocument(document, includedResult.sparseFieldsetLinkageExemptions());
  }

  private static EffectiveRepresentation effectiveRepresentation(
      RepresentationSelection selection, RepresentationPolicy policy) {
    return new EffectiveRepresentation(
        Objects.requireNonNull(selection, SELECTION), Objects.requireNonNull(policy, POLICY));
  }

  private static void rejectNonEmptyFieldsets(EffectiveRepresentation representation) {
    if (!representation.selection().fieldsets().isEmpty()) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.FIELDSETS_REQUIRE_MAPPED_DOCUMENT,
          null,
          "Non-empty fieldsets require toMappedDocument / toMappedCollectionDocument; types: "
              + representation.selection().fieldsets().keySet());
    }
  }

  private static List<Object> materialize(Iterable<?> resources) {
    List<Object> snapshot = new ArrayList<>();
    for (Object resource : resources) {
      Objects.requireNonNull(resource, "resource element");
      snapshot.add(resource);
    }
    return List.copyOf(snapshot);
  }

  private List<JavaType> inferredTypes(List<Object> resources) {
    List<JavaType> types = new ArrayList<>(resources.size());
    for (Object resource : resources) {
      types.add(writer.inferredType(resource));
    }
    return List.copyOf(types);
  }

  private List<JavaType> effectiveTypes(List<Object> resources, List<JavaType> declaredTypes) {
    List<JavaType> types = new ArrayList<>(resources.size());
    for (int i = 0; i < resources.size(); i++) {
      types.add(writer.effectiveType(resources.get(i), declaredTypes.get(i)));
    }
    return List.copyOf(types);
  }

  private static List<JavaType> repeatedType(int size, JavaType resourceType) {
    return java.util.Collections.nCopies(size, resourceType);
  }

  private static JsonApiDocument buildDocument(
      DocumentData data,
      @Nullable DocumentEnvelope envelope,
      @Nullable List<ResourceObject> included) {
    if (envelope == null) {
      return new JsonApiDocument(data, null, null, null, null, included, Map.of());
    }
    return new JsonApiDocument(
        data, null, envelope.meta(), envelope.jsonapi(), envelope.links(), included, Map.of());
  }
}

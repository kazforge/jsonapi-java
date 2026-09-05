package io.github.kazemek.jsonapi.jackson3;

import io.github.kazemek.jsonapi.core.model.DocumentData;
import io.github.kazemek.jsonapi.core.model.JsonApiDocument;
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier;
import io.github.kazemek.jsonapi.core.model.ResourceIdentity;
import io.github.kazemek.jsonapi.core.model.ResourceObject;
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiDocumentReadException;
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingLocation;
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext;
import io.github.kazemek.jsonapi.jackson.mapping.DomainData;
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter;
import io.github.kazemek.jsonapi.jackson.mapping.IncludedResources;
import io.github.kazemek.jsonapi.jackson3.internal.DomainResourceBinder;
import io.github.kazemek.jsonapi.jackson3.internal.MappingDefinitionCache;
import io.github.kazemek.jsonapi.jackson3.internal.MetaBindingModule;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads validated JSON:API documents into an immutable {@link JsonApiDomainDocument} with flat
 * primary DTOs and independently bound included DTOs.
 *
 * <p>Decoding and aggregate validation run exactly as in {@link JsonApiDocumentReader}: {@link
 * #readValue} overloads share its close/ownership rules and keep codec/validation failures as
 * {@link JsonApiDocumentReadException} with the same category, pointer, location, and rule code.
 * {@link #fromDocument(JsonApiDocument)} binds only and never re-parses or re-validates.
 *
 * <p>Primary resource data and every present {@code included} element are bound through the Phase
 * 2.9 binder after looking up {@link ResourceObject#type()} in the supplied {@link
 * ResourceTypeRegistry}; identifier primary data and error documents never attempt DTO binding.
 * Resource types absent from the registry fail with {@link
 * MappingDiagnostic#UNREGISTERED_RESOURCE_TYPE} at the document pointer before any envelope
 * escapes; other binder failures compose structurally with the document pointer ({@code /data},
 * {@code /data/<index>}, {@code /included/<index>}) per the mapping-location contract: a
 * resource-relative binder location joins under the prefix, and a locationless binder failure
 * reports just the prefix. Relationship properties stay linkage-only and {@code included} is never
 * injected.
 *
 * <p>Construct instances via {@link JsonApiJackson3#domainDocumentReader(JsonMapper,
 * DocumentReadContext, ResourceTypeRegistry)} or its overloads, never directly. Construction
 * re-resolves every registered target against the reader's configured resource metadata and rejects
 * keys that disagree with {@link MappingDiagnostic#RESOURCE_TYPE_MISMATCH} without a document
 * location; missing or invalid consumer metadata keeps its existing resolver diagnostic. The reader
 * is safe for concurrent use once created.
 */
public final class JsonApiDomainDocumentReader {

  private final JsonApiDocumentReader documentReader;
  private final ResourceTypeRegistry registry;
  private final JsonMapper binderMapper;
  private final DomainResourceBinder binder;
  private final JsonApiDomainDocument.MetaConverter metaConverter;

  JsonApiDomainDocumentReader(
      JsonMapper base,
      DocumentReadContext context,
      ResourceTypeRegistry registry,
      IdentifierConverter identifierConverter,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
    this.documentReader = new JsonApiDocumentReader(base, context);
    this.registry = Objects.requireNonNull(registry, "registry");
    this.binderMapper = base.rebuild().addModule(new MetaBindingModule()).build();
    MappingDefinitionCache metadataAuthority = new MappingDefinitionCache(binderMapper);
    requireRegistryCoherence(this.registry, metadataAuthority);
    this.binder =
        new DomainResourceBinder(
            binderMapper, identifierConverter, metadataAuthority, linkageMappers);
    this.metaConverter = new BinderMetaConverter(binderMapper);
  }

  private static void requireRegistryCoherence(
      ResourceTypeRegistry registry, MappingDefinitionCache metadataAuthority) {
    for (ResourceTypeRegistry.RegisteredType registered : registry.registrations()) {
      String configuredType = metadataAuthority.requireResourceTypeName(registered.rawClass());
      if (!configuredType.equals(registered.type())) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.RESOURCE_TYPE_MISMATCH,
            registered.rawClass(),
            "Registered JSON:API type '"
                + registered.type()
                + "' for "
                + registered.rawClass().getName()
                + " does not match configured resource type '"
                + configuredType
                + "'");
      }
    }
  }

  /** Decodes, validates, and binds the JSON:API document in the given string. */
  public JsonApiDomainDocument readValue(String json) {
    return fromDocument(documentReader.readValue(json));
  }

  /** Decodes, validates, and binds the UTF-8 JSON:API document in the given bytes. */
  public JsonApiDomainDocument readValue(byte[] utf8Json) {
    return fromDocument(documentReader.readValue(utf8Json));
  }

  /**
   * Decodes, validates, and binds one JSON:API document from a caller-owned UTF-8 stream. The
   * stream is not closed; only the parser created for this call is closed.
   */
  public JsonApiDomainDocument readValue(InputStream utf8Stream) {
    return fromDocument(documentReader.readValue(utf8Stream));
  }

  /**
   * Decodes, validates, and binds one JSON:API document from a caller-owned parser starting at the
   * current token (or the next token if none is current). The parser is not closed.
   */
  public JsonApiDomainDocument readValue(JsonParser parser) {
    return fromDocument(documentReader.readValue(parser));
  }

  /**
   * Binds an already-validated document into a domain envelope; never re-parses or re-validates.
   */
  public JsonApiDomainDocument fromDocument(JsonApiDocument document) {
    Objects.requireNonNull(document, "document");
    return new JsonApiDomainDocument(
        new JsonApiDomainDocument.Components(
            bindData(document.data()),
            document.errors(),
            document.meta(),
            document.jsonapi(),
            document.links(),
            bindIncluded(document.included()),
            document.additionalMembers()),
        metaConverter);
  }

  private @Nullable DomainData bindData(@Nullable DocumentData data) {
    if (data == null) {
      return null;
    }
    return switch (data) {
      case DocumentData.NullData() -> DomainData.NullData.INSTANCE;
      case DocumentData.SingleResource(ResourceObject resource) ->
          new DomainData.SingleResource(bindResource(resource, MappingLocation.of("data")));
      case DocumentData.ResourceCollection(List<ResourceObject> resources) -> {
        List<Object> bound = new ArrayList<>(resources.size());
        for (int i = 0; i < resources.size(); i++) {
          bound.add(
              bindResource(resources.get(i), MappingLocation.of("data", Integer.toString(i))));
        }
        yield new DomainData.ResourceCollection(bound);
      }
      case DocumentData.SingleIdentifier(ResourceIdentifier identifier) ->
          new DomainData.SingleIdentifier(identifier);
      case DocumentData.IdentifierCollection(List<ResourceIdentifier> identifiers) ->
          new DomainData.IdentifierCollection(identifiers);
    };
  }

  private @Nullable IncludedResources bindIncluded(@Nullable List<ResourceObject> included) {
    if (included == null) {
      return null;
    }
    List<Object> bound = new ArrayList<>(included.size());
    List<Set<ResourceIdentity>> identitiesByPosition = new ArrayList<>(included.size());
    Set<ResourceIdentity> seen = new LinkedHashSet<>();
    for (int i = 0; i < included.size(); i++) {
      ResourceObject resource = included.get(i);
      MappingLocation pointer = MappingLocation.of("included", Integer.toString(i));
      Object dto = bindResource(resource, pointer);
      bound.add(dto);
      Set<ResourceIdentity> identities = new LinkedHashSet<>();
      if (resource.hasId()) {
        ResourceIdentity identity =
            ResourceIdentity.ofId(resource.type(), Objects.requireNonNull(resource.id()));
        putIdentity(seen, identity, pointer);
        identities.add(identity);
      }
      if (resource.hasLid()) {
        ResourceIdentity identity =
            ResourceIdentity.ofLid(resource.type(), Objects.requireNonNull(resource.lid()));
        putIdentity(seen, identity, pointer);
        identities.add(identity);
      }
      identitiesByPosition.add(identities);
    }
    return IncludedResources.of(bound, identitiesByPosition);
  }

  private static void putIdentity(
      Set<ResourceIdentity> seen, ResourceIdentity identity, MappingLocation pointer) {
    if (!seen.add(identity)) {
      throw new JsonApiMappingException(
          MappingDiagnostic.CONFLICTING_INCLUDED_REPRESENTATION,
          null,
          pointer,
          "Duplicate included identity " + identity);
    }
  }

  /**
   * Binds one resource under the given document-relative prefix. Registry misses fail at the prefix
   * itself; binder failures compose structurally: the document prefix joins the binder's
   * resource-relative location ({@code /data/2} + {@code /attributes/title} = {@code
   * /data/2/attributes/title}), and a binder failure without a location reports just the document
   * prefix rather than inventing a member.
   */
  private Object bindResource(ResourceObject resource, MappingLocation documentPrefix) {
    ResourceObject checkedResource = Objects.requireNonNull(resource, "resource");
    ResourceTypeRegistry.RegisteredType registered = registry.resolve(checkedResource.type());
    if (registered == null) {
      throw new JsonApiMappingException(
          MappingDiagnostic.UNREGISTERED_RESOURCE_TYPE,
          null,
          documentPrefix,
          "No DTO target registered for JSON:API resource type '" + checkedResource.type() + "'");
    }
    JavaType registeredType = registered.javaType();
    JavaType targetType =
        registeredType != null ? registeredType : binderMapper.constructType(registered.rawClass());
    try {
      return binder.fromResource(checkedResource, targetType);
    } catch (JsonApiMappingException ex) {
      String message = ex.getMessage() != null ? ex.getMessage() : ex.diagnostic().name();
      MappingLocation relative = ex.location();
      MappingLocation composed =
          relative == null ? documentPrefix : documentPrefix.append(relative);
      throw new JsonApiMappingException(ex.diagnostic(), ex.resourceClass(), composed, message, ex);
    }
  }
}

package com.kazforge.jsonapi.jackson2;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.validation.DocumentUsage;
import com.kazforge.jsonapi.core.validation.ValidationContext;
import com.kazforge.jsonapi.jackson.api.JsonApi;
import com.kazforge.jsonapi.jackson.api.JsonApiDocuments;
import com.kazforge.jsonapi.jackson.api.JsonApiPatches;
import com.kazforge.jsonapi.jackson.api.JsonApiRelationships;
import com.kazforge.jsonapi.jackson.api.JsonApiResources;
import com.kazforge.jsonapi.jackson.document.DocumentReadContext;
import com.kazforge.jsonapi.jackson.mapping.IdentifierConverter;
import com.kazforge.jsonapi.jackson.mapping.ResourceDecoratorRegistry;
import com.kazforge.jsonapi.jackson.representation.RepresentationPolicy;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Configured Jackson 2 implementation of the major-neutral Level-1 {@link JsonApi} application
 * contract.
 *
 * <p>Obtain instances through {@link JsonApiJackson2#jsonApi(JsonMapper)} for documented defaults
 * or {@link JsonApiJackson2#builder(JsonMapper)} for coherent application-lifetime configuration
 * (identifier conversion, custom linkage mapping, representation policy, resource decoration, and
 * an optional resource-write JSON:API version default). The runtime is immutable and safe for
 * concurrent use once created.
 *
 * <p>The runtime coordinates the existing capability pipeline internally — resource mapping with
 * configured decoration, mapped-document validation, document writing, document decoding with
 * aggregate validation, flat DTO binding, and PATCH projection — so ordinary callers never
 * orchestrate those phases manually. The advanced capability APIs remain public and unchanged.
 * Jackson 2's checked I/O is adapted at this facade boundary to {@link
 * java.io.UncheckedIOException}; existing document-read, validation, and mapping failures retain
 * their existing exception families.
 *
 * <p>Request-scoped values (representation selection, document envelope, and expected update
 * identity) stay method arguments. An absent per-write {@code jsonapi} member may inherit the
 * configured application-lifetime default on {@link JsonApiResources} writes, while an explicit
 * value always wins completely. Raw document and minimal linkage writes remain explicit.
 */
public final class Jackson2JsonApi implements JsonApi {

  private final Jackson2JsonApiResources resources;
  private final Jackson2JsonApiRelationships relationships;
  private final Jackson2JsonApiDocuments documents;
  private final Jackson2JsonApiPatches patches;

  Jackson2JsonApi(
      JsonMapper baseMapper,
      IdentifierConverter identifierConverter,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers,
      RepresentationPolicy representationPolicy,
      ResourceDecoratorRegistry decorators,
      @Nullable JsonApiObject defaultJsonApi) {
    Objects.requireNonNull(baseMapper, "baseMapper");
    Objects.requireNonNull(identifierConverter, "identifierConverter");
    Objects.requireNonNull(linkageMappers, "linkageMappers");
    Objects.requireNonNull(representationPolicy, "representationPolicy");
    Objects.requireNonNull(decorators, "decorators");
    JsonMapper documentMapper = JsonApiJackson2.documentMapper(baseMapper);
    JsonApiResourceMapper resourceMapper =
        JsonApiJackson2.resourceMapper(baseMapper, identifierConverter, decorators);
    JsonApiResourceBinder resourceBinder =
        JsonApiJackson2.resourceBinder(baseMapper, identifierConverter, linkageMappers);
    JsonApiDocumentReader resourceReader =
        new JsonApiDocumentReader(baseMapper, DocumentReadContext.resourceDefaults());
    JsonApiDocumentReader identifierReader =
        new JsonApiDocumentReader(baseMapper, DocumentReadContext.identifierDefaults());
    JsonApiDocumentWriter responseWriter =
        new JsonApiDocumentWriter(documentMapper, ValidationContext.defaults());
    JsonApiDocumentWriter createWriter =
        new JsonApiDocumentWriter(
            documentMapper,
            ValidationContext.defaults().withDocumentUsage(DocumentUsage.CREATE_REQUEST));
    JsonApiPatchCommandReader patchCommandReader =
        JsonApiJackson2.patchCommandReader(
            baseMapper, ValidationContext.defaults(), identifierConverter, linkageMappers);
    JsonApiPatchDtoReader patchDtoReader =
        JsonApiJackson2.patchDtoReader(
            baseMapper, ValidationContext.defaults(), identifierConverter, linkageMappers);
    this.resources =
        new Jackson2JsonApiResources(
            baseMapper,
            new Jackson2JsonApiResources.ResourceConfiguration(
                representationPolicy, defaultJsonApi),
            resourceMapper,
            resourceBinder,
            resourceReader,
            responseWriter,
            createWriter);
    this.relationships = new Jackson2JsonApiRelationships(identifierReader, responseWriter);
    this.documents = new Jackson2JsonApiDocuments(baseMapper, responseWriter);
    this.patches = new Jackson2JsonApiPatches(baseMapper, patchCommandReader, patchDtoReader);
  }

  @Override
  public JsonApiResources resources() {
    return resources;
  }

  @Override
  public JsonApiRelationships relationships() {
    return relationships;
  }

  @Override
  public JsonApiDocuments documents() {
    return documents;
  }

  @Override
  public JsonApiPatches patches() {
    return patches;
  }

  /** Mutable builder for one immutable {@link Jackson2JsonApi}. */
  public static final class Builder {

    private final JsonMapper baseMapper;
    private IdentifierConverter identifierConverter = IdentifierConverter.defaults();
    private Map<Class<?>, RelationshipLinkageMapper> linkageMappers = Map.of();
    private RepresentationPolicy representationPolicy = RepresentationPolicy.defaults();
    private ResourceDecoratorRegistry decorators = ResourceDecoratorRegistry.empty();
    private @Nullable JsonApiObject defaultJsonApi;

    Builder(JsonMapper baseMapper) {
      this.baseMapper = Objects.requireNonNull(baseMapper, "baseMapper");
    }

    /** Uses the given identifier converter for both identity roles. */
    public Builder identifierConverter(IdentifierConverter identifierConverter) {
      this.identifierConverter = Objects.requireNonNull(identifierConverter, "identifierConverter");
      return this;
    }

    /** Uses custom relationship linkage mappers keyed by relationship target class. */
    public Builder linkageMappers(Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
      Objects.requireNonNull(linkageMappers, "linkageMappers");
      this.linkageMappers = Map.copyOf(linkageMappers);
      return this;
    }

    /** Uses the given representation policy as the application default. */
    public Builder representationPolicy(RepresentationPolicy representationPolicy) {
      this.representationPolicy =
          Objects.requireNonNull(representationPolicy, "representationPolicy");
      return this;
    }

    /** Uses the given resource decorator registry for domain writes. */
    public Builder decorators(ResourceDecoratorRegistry decorators) {
      this.decorators = Objects.requireNonNull(decorators, "decorators");
      return this;
    }

    /**
     * Advertises the given JSON:API version on resource documents written by this runtime when a
     * write does not supply an explicit {@code jsonapi} object. This setting describes the document
     * {@code jsonapi.version}; it is not HTTP API or business versioning and does not negotiate
     * media type extensions or profiles.
     */
    public Builder jsonApiVersion(String jsonApiVersion) {
      this.defaultJsonApi =
          JsonApiObject.ofVersion(Objects.requireNonNull(jsonApiVersion, "jsonApiVersion"));
      return this;
    }

    /** Builds an immutable runtime. */
    public Jackson2JsonApi build() {
      return new Jackson2JsonApi(
          baseMapper,
          identifierConverter,
          Map.copyOf(linkageMappers),
          representationPolicy,
          decorators,
          defaultJsonApi);
    }
  }
}

package io.github.kazemek.jsonapi.jackson3;

import io.github.kazemek.jsonapi.core.validation.DocumentUsage;
import io.github.kazemek.jsonapi.core.validation.ValidationContext;
import io.github.kazemek.jsonapi.jackson.api.JsonApi;
import io.github.kazemek.jsonapi.jackson.api.JsonApiDocuments;
import io.github.kazemek.jsonapi.jackson.api.JsonApiPatches;
import io.github.kazemek.jsonapi.jackson.api.JsonApiRelationships;
import io.github.kazemek.jsonapi.jackson.api.JsonApiResources;
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext;
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter;
import io.github.kazemek.jsonapi.jackson.mapping.ResourceDecoratorRegistry;
import io.github.kazemek.jsonapi.jackson.representation.RepresentationPolicy;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configured Jackson 3 implementation of the major-neutral Level-1 {@link JsonApi} application
 * contract.
 *
 * <p>Obtain instances through {@link JsonApiJackson3#jsonApi(JsonMapper)} for documented defaults
 * or {@link JsonApiJackson3#builder(JsonMapper)} for coherent application-lifetime configuration
 * (identifier conversion, custom linkage mapping, representation policy, and resource decoration).
 * The runtime is immutable and safe for concurrent use once created.
 *
 * <p>The runtime coordinates the existing capability pipeline internally — resource mapping with
 * configured decoration, mapped-document validation, document writing, document decoding with
 * aggregate validation, flat DTO binding, and PATCH projection — so ordinary callers never
 * orchestrate those phases manually. The advanced capability APIs remain public and unchanged.
 *
 * <p>Request-scoped values (representation selection, document envelope, expected update identity,
 * per-write {@code jsonapi} object) stay method arguments, never runtime configuration. An absent
 * per-write {@code jsonapi} member remains distinct from an explicit value, leaving room for a
 * future application-lifetime default to apply only when the caller supplied none.
 */
public final class Jackson3JsonApi implements JsonApi {

  private final Jackson3JsonApiResources resources;
  private final Jackson3JsonApiRelationships relationships;
  private final Jackson3JsonApiDocuments documents;
  private final Jackson3JsonApiPatches patches;

  Jackson3JsonApi(
      JsonMapper baseMapper,
      IdentifierConverter identifierConverter,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers,
      RepresentationPolicy representationPolicy,
      ResourceDecoratorRegistry decorators) {
    Objects.requireNonNull(baseMapper, "baseMapper");
    Objects.requireNonNull(representationPolicy, "representationPolicy");
    JsonMapper documentMapper = JsonApiJackson3.documentMapper(baseMapper);
    JsonApiResourceMapper resourceMapper =
        JsonApiJackson3.resourceMapper(baseMapper, identifierConverter, decorators);
    JsonApiResourceBinder resourceBinder =
        JsonApiJackson3.resourceBinder(baseMapper, identifierConverter, linkageMappers);
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
        JsonApiJackson3.patchCommandReader(
            baseMapper, ValidationContext.defaults(), identifierConverter, linkageMappers);
    JsonApiPatchDtoReader patchDtoReader =
        JsonApiJackson3.patchDtoReader(
            baseMapper, ValidationContext.defaults(), identifierConverter, linkageMappers);
    this.resources =
        new Jackson3JsonApiResources(
            baseMapper,
            representationPolicy,
            resourceMapper,
            resourceBinder,
            resourceReader,
            responseWriter,
            createWriter);
    this.relationships = new Jackson3JsonApiRelationships(identifierReader, responseWriter);
    this.documents = new Jackson3JsonApiDocuments(baseMapper, responseWriter);
    this.patches = new Jackson3JsonApiPatches(baseMapper, patchCommandReader, patchDtoReader);
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

  /**
   * Mutable builder for one immutable {@link Jackson3JsonApi}. Every setting except the mapper is
   * optional and selects the same documented default as the corresponding capability factory.
   */
  public static final class Builder {

    private final JsonMapper baseMapper;
    private IdentifierConverter identifierConverter = IdentifierConverter.defaults();
    private Map<Class<?>, RelationshipLinkageMapper> linkageMappers = Map.of();
    private RepresentationPolicy representationPolicy = RepresentationPolicy.defaults();
    private ResourceDecoratorRegistry decorators = ResourceDecoratorRegistry.empty();

    Builder(JsonMapper baseMapper) {
      this.baseMapper = Objects.requireNonNull(baseMapper, "baseMapper");
    }

    /** Uses the given identifier converter for both identity roles. */
    public Builder identifierConverter(IdentifierConverter identifierConverter) {
      this.identifierConverter = Objects.requireNonNull(identifierConverter, "identifierConverter");
      return this;
    }

    /**
     * Uses custom relationship linkage mappers keyed by relationship target class. The map is
     * snapshotted on {@link #build()}.
     */
    public Builder linkageMappers(Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
      Objects.requireNonNull(linkageMappers, "linkageMappers");
      this.linkageMappers = Map.copyOf(linkageMappers);
      return this;
    }

    /**
     * Uses the given representation policy as the application default governing per-operation
     * representation selections. Default writes inherit this policy; per-call overrides remain
     * advanced.
     */
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

    /** Builds an immutable runtime. */
    public Jackson3JsonApi build() {
      return new Jackson3JsonApi(
          baseMapper,
          identifierConverter,
          Map.copyOf(linkageMappers),
          representationPolicy,
          decorators);
    }
  }
}

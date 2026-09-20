package com.kazforge.jsonapi.jackson3;

import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.api.JsonApiDocuments;
import com.kazforge.jsonapi.api.JsonApiPatches;
import com.kazforge.jsonapi.api.JsonApiRelationships;
import com.kazforge.jsonapi.api.JsonApiResources;
import com.kazforge.jsonapi.core.aggregate.ValidationContext;
import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.validation.DocumentUsage;
import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import com.kazforge.jsonapi.document.DocumentReadContext;
import com.kazforge.jsonapi.document.PrimaryDataKind;
import com.kazforge.jsonapi.jackson3.internal.DomainResourceBinder;
import com.kazforge.jsonapi.jackson3.internal.DomainResourceWriter;
import com.kazforge.jsonapi.jackson3.internal.MappingDefinitionCache;
import com.kazforge.jsonapi.jackson3.mapping.RelationshipLinkageMapper;
import com.kazforge.jsonapi.mapping.IdentifierConverter;
import com.kazforge.jsonapi.mapping.ResourceDecoratorRegistry;
import com.kazforge.jsonapi.representation.RepresentationPolicy;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configured Jackson 3 implementation of the major-neutral Level-1 {@link JsonApi} application
 * contract.
 *
 * <p>Obtain instances through {@link JsonApiJackson3#jsonApi(JsonMapper)} for documented defaults
 * or {@link JsonApiJackson3#builder(JsonMapper)} for coherent application-lifetime configuration
 * (identifier conversion, custom linkage mapping, representation policy, resource decoration, and
 * an optional resource-write JSON:API version default). The runtime is immutable and safe for
 * concurrent use once created.
 *
 * <p>The runtime coordinates the capability pipeline internally: resource mapping with configured
 * decoration, mapped-document validation, document writing, document decoding with aggregate
 * validation, flat DTO binding, and PATCH projection — so ordinary callers never orchestrate those
 * steps manually. The advanced capability APIs are also available.
 *
 * <p>Request-scoped values (representation selection, document envelope, and expected update
 * identity) stay method arguments. An absent per-write {@code jsonapi} member may inherit the
 * configured application-lifetime default on {@link JsonApiResources} writes, while an explicit
 * value always wins completely. Raw document and minimal linkage writes remain explicit. The
 * relationship facet explicitly composes identifier decoding with the relationship endpoint role;
 * the ordinary response writer stays on the resource endpoint role for resources and raw documents.
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
      ResourceDecoratorRegistry decorators,
      @Nullable JsonApiObject defaultJsonApi) {
    Objects.requireNonNull(baseMapper, "baseMapper");
    Objects.requireNonNull(representationPolicy, "representationPolicy");
    JsonMapper documentMapper = JsonApiJackson3Assembly.documentMapper(baseMapper);
    JsonMapper mappingMapper = JsonApiJackson3Assembly.resourceMappingMapper(baseMapper);
    JsonApiResourceMapper resourceMapper =
        new JsonApiResourceMapper(
            new DomainResourceWriter(
                mappingMapper,
                identifierConverter,
                new MappingDefinitionCache(mappingMapper),
                decorators));
    JsonApiResourceBinder resourceBinder =
        new JsonApiResourceBinder(
            mappingMapper,
            new DomainResourceBinder(
                mappingMapper,
                identifierConverter,
                new MappingDefinitionCache(mappingMapper),
                linkageMappers));
    JsonApiDocumentReader resourceReader =
        new JsonApiDocumentReader(baseMapper, DocumentReadContext.resourceDefaults());
    JsonApiDocumentReader relationshipReader =
        new JsonApiDocumentReader(
            baseMapper,
            DocumentReadContext.of(
                ValidationContext.defaults()
                    .withPrimaryDataContext(PrimaryDataContext.RELATIONSHIP),
                PrimaryDataKind.RESOURCE_IDENTIFIER));
    JsonApiDocumentWriter responseWriter =
        new JsonApiDocumentWriter(documentMapper, ValidationContext.defaults());
    JsonApiDocumentWriter relationshipWriter =
        new JsonApiDocumentWriter(
            documentMapper,
            ValidationContext.defaults().withPrimaryDataContext(PrimaryDataContext.RELATIONSHIP));
    JsonApiDocumentWriter createWriter =
        new JsonApiDocumentWriter(
            documentMapper,
            ValidationContext.defaults().withDocumentUsage(DocumentUsage.CREATE_REQUEST));
    JsonApiPatchCommandReader patchCommandReader =
        new JsonApiPatchCommandReader(
            baseMapper, ValidationContext.defaults(), identifierConverter, linkageMappers);
    JsonApiPatchDtoReader patchDtoReader =
        new JsonApiPatchDtoReader(
            baseMapper, ValidationContext.defaults(), identifierConverter, linkageMappers);
    this.resources =
        new Jackson3JsonApiResources(
            baseMapper,
            new Jackson3JsonApiResources.ResourceConfiguration(
                representationPolicy, defaultJsonApi),
            resourceMapper,
            resourceBinder,
            resourceReader,
            responseWriter,
            createWriter);
    this.relationships = new Jackson3JsonApiRelationships(relationshipReader, relationshipWriter);
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
    private @Nullable JsonApiObject defaultJsonApi;

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

    /**
     * Advertises the given JSON:API version on resource documents written by this runtime when a
     * write does not supply an explicit {@code jsonapi} object. This setting describes the document
     * {@code jsonapi.version}; it is not HTTP API or business versioning and does not negotiate
     * media type extensions or profiles.
     *
     * <p>The supplied value is preserved as-is and must not be {@code null}.
     */
    public Builder jsonApiVersion(String jsonApiVersion) {
      this.defaultJsonApi =
          JsonApiObject.ofVersion(Objects.requireNonNull(jsonApiVersion, "jsonApiVersion"));
      return this;
    }

    /** Builds an immutable runtime. */
    public Jackson3JsonApi build() {
      return new Jackson3JsonApi(
          baseMapper,
          identifierConverter,
          Map.copyOf(linkageMappers),
          representationPolicy,
          decorators,
          defaultJsonApi);
    }
  }
}

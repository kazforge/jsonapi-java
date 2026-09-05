package io.github.kazemek.jsonapi.jackson3;

import io.github.kazemek.jsonapi.core.model.DocumentData;
import io.github.kazemek.jsonapi.core.model.JsonApiDocument;
import io.github.kazemek.jsonapi.core.model.ResourceObject;
import io.github.kazemek.jsonapi.core.validation.DocumentUsage;
import io.github.kazemek.jsonapi.core.validation.EndpointIdentity;
import io.github.kazemek.jsonapi.core.validation.ValidationContext;
import io.github.kazemek.jsonapi.jackson.api.JsonApiResources;
import io.github.kazemek.jsonapi.jackson.api.ResourceCollectionDocument;
import io.github.kazemek.jsonapi.jackson.api.ResourceDocument;
import io.github.kazemek.jsonapi.jackson.api.ResourceWriteOptions;
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingLocation;
import io.github.kazemek.jsonapi.jackson.mapping.MappedDocument;
import io.github.kazemek.jsonapi.jackson.representation.RepresentationPolicy;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 Level-1 resource operations: strict homogeneous reads, single/collection writes, and
 * create/update authoring.
 *
 * <p>Reads decode and fully validate the document, require the declared primary shape, and bind
 * directly to the caller type without a resource-type registry. Writes map with configured
 * decoration, validate the mapped document, and emit — including sparse-fieldset provenance —
 * without caller choreography.
 */
final class Jackson3JsonApiResources implements JsonApiResources {

  private static final String RESOURCE = "resource";
  private static final String OPTIONS = "options";

  private final JsonMapper baseMapper;
  private final RepresentationPolicy representationPolicy;
  private final JsonApiResourceMapper resourceMapper;
  private final JsonApiResourceBinder resourceBinder;
  private final JsonApiDocumentReader resourceReader;
  private final JsonApiDocumentWriter responseWriter;
  private final JsonApiDocumentWriter createWriter;

  Jackson3JsonApiResources(
      JsonMapper baseMapper,
      RepresentationPolicy representationPolicy,
      JsonApiResourceMapper resourceMapper,
      JsonApiResourceBinder resourceBinder,
      JsonApiDocumentReader resourceReader,
      JsonApiDocumentWriter responseWriter,
      JsonApiDocumentWriter createWriter) {
    this.baseMapper = Objects.requireNonNull(baseMapper, "baseMapper");
    this.representationPolicy =
        Objects.requireNonNull(representationPolicy, "representationPolicy");
    this.resourceMapper = Objects.requireNonNull(resourceMapper, "resourceMapper");
    this.resourceBinder = Objects.requireNonNull(resourceBinder, "resourceBinder");
    this.resourceReader = Objects.requireNonNull(resourceReader, "resourceReader");
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    this.createWriter = Objects.requireNonNull(createWriter, "createWriter");
  }

  @Override
  public <T> T readOne(String json, Class<T> type) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(type, "type");
    return type.cast(bindSingle(resourceReader.readValue(json), type));
  }

  @Override
  public <T> T readOne(InputStream json, Class<T> type) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(type, "type");
    return type.cast(bindSingle(resourceReader.readValue(json), type));
  }

  @Override
  public Object readOne(String json, Type type) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(type, "type");
    return bindSingle(resourceReader.readValue(json), baseMapper.constructType(type));
  }

  @Override
  public Object readOne(InputStream json, Type type) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(type, "type");
    return bindSingle(resourceReader.readValue(json), baseMapper.constructType(type));
  }

  @Override
  public <T> List<T> readMany(String json, Class<T> type) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(type, "type");
    return bindCollection(resourceReader.readValue(json), baseMapper.constructType(type), type);
  }

  @Override
  public <T> List<T> readMany(InputStream json, Class<T> type) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(type, "type");
    return bindCollection(resourceReader.readValue(json), baseMapper.constructType(type), type);
  }

  @Override
  public List<Object> readMany(String json, Type type) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(type, "type");
    return bindCollection(resourceReader.readValue(json), baseMapper.constructType(type), null);
  }

  @Override
  public List<Object> readMany(InputStream json, Type type) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(type, "type");
    return bindCollection(resourceReader.readValue(json), baseMapper.constructType(type), null);
  }

  @Override
  public <T> ResourceDocument<T> readOneDocument(String json, Class<T> type) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(type, "type");
    return documentSingle(resourceReader.readValue(json), type);
  }

  @Override
  public <T> ResourceDocument<T> readOneDocument(InputStream json, Class<T> type) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(type, "type");
    return documentSingle(resourceReader.readValue(json), type);
  }

  @Override
  public <T> ResourceCollectionDocument<T> readManyDocument(String json, Class<T> type) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(type, "type");
    return documentCollection(resourceReader.readValue(json), type);
  }

  @Override
  public <T> ResourceCollectionDocument<T> readManyDocument(InputStream json, Class<T> type) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(type, "type");
    return documentCollection(resourceReader.readValue(json), type);
  }

  @Override
  public String writeOne(Object resource) {
    return writeOne(resource, ResourceWriteOptions.defaults());
  }

  @Override
  public String writeOne(Object resource, ResourceWriteOptions options) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(options, OPTIONS);
    return responseWriter.writeValueAsString(mappedSingle(resource, options));
  }

  @Override
  public void writeOne(Object resource, OutputStream out) {
    writeOne(resource, ResourceWriteOptions.defaults(), out);
  }

  @Override
  public void writeOne(Object resource, ResourceWriteOptions options, OutputStream out) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(options, OPTIONS);
    Objects.requireNonNull(out, "out");
    responseWriter.writeValue(out, mappedSingle(resource, options));
  }

  @Override
  public String writeMany(Iterable<?> resources) {
    return writeMany(resources, ResourceWriteOptions.defaults());
  }

  @Override
  public String writeMany(Iterable<?> resources, ResourceWriteOptions options) {
    Objects.requireNonNull(resources, "resources");
    Objects.requireNonNull(options, OPTIONS);
    return responseWriter.writeValueAsString(mappedCollection(resources, options));
  }

  @Override
  public void writeMany(Iterable<?> resources, OutputStream out) {
    writeMany(resources, ResourceWriteOptions.defaults(), out);
  }

  @Override
  public void writeMany(Iterable<?> resources, ResourceWriteOptions options, OutputStream out) {
    Objects.requireNonNull(resources, "resources");
    Objects.requireNonNull(options, OPTIONS);
    Objects.requireNonNull(out, "out");
    responseWriter.writeValue(out, mappedCollection(resources, options));
  }

  @Override
  public String writeCreateDocument(Object resource) {
    return writeCreateDocument(resource, ResourceWriteOptions.defaults());
  }

  @Override
  public String writeCreateDocument(Object resource, ResourceWriteOptions options) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(options, OPTIONS);
    return createWriter.writeValueAsString(mappedCreate(resource, options));
  }

  @Override
  public void writeCreateDocument(Object resource, OutputStream out) {
    writeCreateDocument(resource, ResourceWriteOptions.defaults(), out);
  }

  @Override
  public void writeCreateDocument(Object resource, ResourceWriteOptions options, OutputStream out) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(options, OPTIONS);
    Objects.requireNonNull(out, "out");
    createWriter.writeValue(out, mappedCreate(resource, options));
  }

  @Override
  public String writeUpdateDocument(Object resource, @Nullable EndpointIdentity expectedIdentity) {
    Objects.requireNonNull(resource, RESOURCE);
    return updateWriter(expectedIdentity)
        .writeValueAsString(mappedSingle(resource, ResourceWriteOptions.defaults()));
  }

  @Override
  public String writeUpdateDocument(
      Object resource, @Nullable EndpointIdentity expectedIdentity, ResourceWriteOptions options) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(options, OPTIONS);
    return updateWriter(expectedIdentity).writeValueAsString(mappedSingle(resource, options));
  }

  @Override
  public void writeUpdateDocument(
      Object resource, @Nullable EndpointIdentity expectedIdentity, OutputStream out) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(out, "out");
    updateWriter(expectedIdentity)
        .writeValue(out, mappedSingle(resource, ResourceWriteOptions.defaults()));
  }

  @Override
  public void writeUpdateDocument(
      Object resource,
      @Nullable EndpointIdentity expectedIdentity,
      ResourceWriteOptions options,
      OutputStream out) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(options, OPTIONS);
    Objects.requireNonNull(out, "out");
    updateWriter(expectedIdentity).writeValue(out, mappedSingle(resource, options));
  }

  private Object bindSingle(JsonApiDocument document, Class<?> type) {
    ResourceObject resource = requireSingleResource(document, type);
    return resourceBinder.fromResource(resource, baseMapper.constructType(type));
  }

  private Object bindSingle(JsonApiDocument document, JavaType javaType) {
    ResourceObject resource = requireSingleResource(document, javaType.getRawClass());
    return resourceBinder.fromResource(resource, javaType);
  }

  private <T> List<T> bindCollection(
      JsonApiDocument document, JavaType javaType, @Nullable Class<T> type) {
    List<ResourceObject> resources = requireResourceCollection(document);
    List<Object> bound = resourceBinder.fromResources(resources, javaType);
    List<T> narrowed = new ArrayList<>(bound.size());
    for (Object item : bound) {
      narrowed.add(type == null ? castUnchecked(item) : type.cast(item));
    }
    return List.copyOf(narrowed);
  }

  @SuppressWarnings("unchecked")
  private static <T> T castUnchecked(Object item) {
    return (T) item;
  }

  private <T> ResourceDocument<T> documentSingle(JsonApiDocument document, Class<T> type) {
    ResourceObject resource = requireSingleResource(document, type);
    T bound = type.cast(resourceBinder.fromResource(resource, baseMapper.constructType(type)));
    return new ResourceDocument<>(
        bound, document.meta(), document.links(), document.jsonapi(), document.included());
  }

  private <T> ResourceCollectionDocument<T> documentCollection(
      JsonApiDocument document, Class<T> type) {
    List<T> bound = bindCollection(document, baseMapper.constructType(type), type);
    return new ResourceCollectionDocument<>(
        bound, document.meta(), document.links(), document.jsonapi(), document.included());
  }

  private MappedDocument mappedSingle(Object resource, ResourceWriteOptions options) {
    return resourceMapper.toMappedDocument(
        resource, options.envelope(), options.selection(), representationPolicy);
  }

  private MappedDocument mappedCreate(Object resource, ResourceWriteOptions options) {
    return resourceMapper.toMappedCreateDocument(
        resource, options.envelope(), options.selection(), representationPolicy);
  }

  private MappedDocument mappedCollection(Iterable<?> resources, ResourceWriteOptions options) {
    return resourceMapper.toMappedCollectionDocument(
        resources, options.envelope(), options.selection(), representationPolicy);
  }

  private JsonApiDocumentWriter updateWriter(@Nullable EndpointIdentity expectedIdentity) {
    // Update authoring shares the response writer's codec mapper; only the usage changes.
    return new JsonApiDocumentWriter(
        createWriter.mapper(),
        ValidationContext.defaults()
            .withDocumentUsage(DocumentUsage.UPDATE_REQUEST)
            .withExpectedEndpointIdentity(expectedIdentity));
  }

  private static ResourceObject requireSingleResource(
      JsonApiDocument document, @Nullable Class<?> type) {
    if (document.data() instanceof DocumentData.SingleResource(ResourceObject resource)) {
      return resource;
    }
    throw shapeMismatch(type, "single-resource", describe(document));
  }

  private static List<ResourceObject> requireResourceCollection(JsonApiDocument document) {
    if (document.data() instanceof DocumentData.ResourceCollection(List<ResourceObject> items)) {
      return items;
    }
    throw shapeMismatch(null, "resource-collection", describe(document));
  }

  private static JsonApiMappingException shapeMismatch(
      @Nullable Class<?> type, String expected, String actual) {
    return new JsonApiMappingException(
        MappingDiagnostic.RESOURCE_TYPE_MISMATCH,
        type,
        MappingLocation.of("data"),
        "Level-1 read requires " + expected + " primary data but found " + actual);
  }

  private static String describe(JsonApiDocument document) {
    if (document.errors() != null) {
      return "an error document";
    }
    return switch (document.data()) {
      case null -> "absent data";
      case DocumentData.NullData ignored -> "explicit null data";
      case DocumentData.SingleResource ignored -> "single-resource data";
      case DocumentData.ResourceCollection ignored -> "resource-collection data";
      case DocumentData.SingleIdentifier ignored -> "single-identifier data";
      case DocumentData.IdentifierCollection ignored -> "identifier-collection data";
    };
  }
}

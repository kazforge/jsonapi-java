package io.github.kazemek.jsonapi.jackson2;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.kazemek.jsonapi.core.model.JsonApiDocument;
import io.github.kazemek.jsonapi.jackson.api.JsonApiPatches;
import io.github.kazemek.jsonapi.jackson.patch.PatchCommand;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Jackson 2 Level-1 presence-aware PATCH operations.
 *
 * <p>Typed {@code PatchPresence} DTO binding is the conventional path; {@link PatchCommand} remains
 * the explicit generic/infrastructure projection of the same validated update document. Neither
 * path depends on global resource-type registration and neither reads {@code included}. Checked
 * Jackson 2 I/O is exposed as {@link java.io.UncheckedIOException} at this Level-1 boundary.
 */
final class Jackson2JsonApiPatches implements JsonApiPatches {

  private static final String DTO_TYPE = "dtoType";
  private static final String RESOURCE_TYPE = "resourceType";
  private static final String DOCUMENT = "document";

  private final JsonMapper baseMapper;
  private final JsonApiPatchCommandReader patchCommandReader;
  private final JsonApiPatchDtoReader patchDtoReader;

  Jackson2JsonApiPatches(
      JsonMapper baseMapper,
      JsonApiPatchCommandReader patchCommandReader,
      JsonApiPatchDtoReader patchDtoReader) {
    this.baseMapper = Objects.requireNonNull(baseMapper, "baseMapper");
    this.patchCommandReader = Objects.requireNonNull(patchCommandReader, "patchCommandReader");
    this.patchDtoReader = Objects.requireNonNull(patchDtoReader, "patchDtoReader");
  }

  @Override
  public <T> T readPatch(String json, Class<T> dtoType) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(dtoType, DTO_TYPE);
    return Jackson2Io.call(() -> patchDtoReader.readValue(json, dtoType));
  }

  @Override
  public <T> T readPatch(InputStream json, Class<T> dtoType) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(dtoType, DTO_TYPE);
    return Jackson2Io.call(() -> patchDtoReader.readValue(json, dtoType));
  }

  @Override
  public Object readPatch(String json, Type dtoType) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(dtoType, DTO_TYPE);
    return Jackson2Io.call(() -> patchDtoReader.readValue(json, baseMapper.constructType(dtoType)));
  }

  @Override
  public Object readPatch(InputStream json, Type dtoType) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(dtoType, DTO_TYPE);
    return Jackson2Io.call(() -> patchDtoReader.readValue(json, baseMapper.constructType(dtoType)));
  }

  @Override
  public <T> PatchCommand<T> readCommand(String json, Class<T> resourceType) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    return Jackson2Io.call(() -> patchCommandReader.readValue(json, resourceType));
  }

  @Override
  public <T> PatchCommand<T> readCommand(InputStream json, Class<T> resourceType) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    return Jackson2Io.call(() -> patchCommandReader.readValue(json, resourceType));
  }

  @Override
  @SuppressWarnings("java:S1452")
  public PatchCommand<?> readCommand(String json, Type resourceType) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    return Jackson2Io.call(
        () -> patchCommandReader.readValue(json, baseMapper.constructType(resourceType)));
  }

  @Override
  @SuppressWarnings("java:S1452")
  public PatchCommand<?> readCommand(InputStream json, Type resourceType) {
    Objects.requireNonNull(json, "json");
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    return Jackson2Io.call(
        () -> patchCommandReader.readValue(json, baseMapper.constructType(resourceType)));
  }

  @Override
  public <T> T bindPatch(JsonApiDocument document, Class<T> dtoType) {
    Objects.requireNonNull(document, DOCUMENT);
    Objects.requireNonNull(dtoType, DTO_TYPE);
    return patchDtoReader.fromDocument(document, dtoType);
  }

  @Override
  public Object bindPatch(JsonApiDocument document, Type dtoType) {
    Objects.requireNonNull(document, DOCUMENT);
    Objects.requireNonNull(dtoType, DTO_TYPE);
    return patchDtoReader.fromDocument(document, baseMapper.constructType(dtoType));
  }

  @Override
  public <T> PatchCommand<T> bindCommand(JsonApiDocument document, Class<T> resourceType) {
    Objects.requireNonNull(document, DOCUMENT);
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    return patchCommandReader.fromDocument(document, resourceType);
  }

  @Override
  @SuppressWarnings("java:S1452")
  public PatchCommand<?> bindCommand(JsonApiDocument document, Type resourceType) {
    Objects.requireNonNull(document, DOCUMENT);
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    return patchCommandReader.fromDocument(document, baseMapper.constructType(resourceType));
  }
}

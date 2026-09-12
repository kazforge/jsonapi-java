package com.kazforge.jsonapi.jackson2;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage;
import java.io.IOException;

/**
 * Identifier-meta conversion fixtures owned by {@code IdentifierMetaMappingSpec} (ADR-017): generic
 * {@code JavaType} preservation, configured naming, and custom serializers. Adapter-local Jackson 2
 * copies of the shared shapes; custom serializers use Jackson 2 SPI.
 */
public final class IdentifierMetaFixtures {

  private IdentifierMetaFixtures() {}

  /** Generic structured value used to prove identifier-meta {@code JavaType} preservation. */
  public record IdMetaBox<T>(T value) {}

  @JsonApiResource(type = "articles")
  public record GenericIdentifierMetaArticle(
      @JsonApiId String id,
      @JsonApiRelationship RelationshipLinkage<ResourceIdentifier, IdMetaBox<Integer>> author) {}

  @JsonApiResource(type = "articles")
  public record SnakeIdentifierMeta(
      @JsonApiId String id,
      @JsonApiRelationship RelationshipLinkage<ResourceIdentifier, SnakeIdMeta> author) {}

  public record SnakeIdMeta(String displayRole) {}

  @JsonApiResource(type = "articles")
  public record SerializedIdentifierMetaArticle(
      @JsonApiId String id,
      @JsonApiRelationship RelationshipLinkage<ResourceIdentifier, EncodedIdMeta> author) {}

  @JsonSerialize(using = EncodedIdMetaSerializer.class)
  public record EncodedIdMeta(String role) {}

  public static final class EncodedIdMetaSerializer extends JsonSerializer<EncodedIdMeta> {
    @Override
    public void serialize(EncodedIdMeta value, JsonGenerator generator, SerializerProvider context)
        throws IOException {
      generator.writeStartObject();
      generator.writeFieldName("encoded");
      generator.writeString(value.role());
      generator.writeEndObject();
    }
  }

  @JsonApiResource(type = "articles")
  public record NonEmittingIdentifierMetaArticle(
      @JsonApiId String id,
      @JsonApiRelationship RelationshipLinkage<ResourceIdentifier, SilentIdMeta> author) {}

  @JsonSerialize(using = SilentIdMetaSerializer.class)
  public record SilentIdMeta(String role) {}

  public static final class SilentIdMetaSerializer extends JsonSerializer<SilentIdMeta> {
    @Override
    public void serialize(SilentIdMeta value, JsonGenerator generator, SerializerProvider context) {
      // Emit nothing so overlay is skipped and existing identifier meta is preserved.
    }
  }

  @JsonApiResource(type = "articles")
  public record ScalarSerializedMetaArticle(
      @JsonApiId String id,
      @JsonApiRelationship RelationshipLinkage<ResourceIdentifier, ScalarIdMeta> author) {}

  @JsonSerialize(using = ScalarIdMetaSerializer.class)
  public record ScalarIdMeta(String role) {}

  public static final class ScalarIdMetaSerializer extends JsonSerializer<ScalarIdMeta> {
    @Override
    public void serialize(ScalarIdMeta value, JsonGenerator generator, SerializerProvider context)
        throws IOException {
      generator.writeString(value.role());
    }
  }
}

package com.kazforge.jsonapi.jackson3;

import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorIdMeta;
import com.kazforge.jsonapi.fixtures.domainpatch.CommentIdMeta;
import com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage;
import java.util.Set;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * Identifier-meta conversion fixtures owned by {@code IdentifierMetaMappingSpec} (ADR-017): generic
 * {@code JavaType} preservation, configured naming, custom serializers, and custom linkage-mapper
 * targets. Shared wrapper/container/lid/additional-member carriers live in the Jackson API test
 * fixtures.
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

  public static final class EncodedIdMetaSerializer extends ValueSerializer<EncodedIdMeta> {
    @Override
    public void serialize(
        EncodedIdMeta value, JsonGenerator generator, SerializationContext context) {
      generator.writeStartObject();
      generator.writeName("encoded");
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

  public static final class SilentIdMetaSerializer extends ValueSerializer<SilentIdMeta> {
    @Override
    public void serialize(
        SilentIdMeta value, JsonGenerator generator, SerializationContext context) {
      // Emit nothing so overlay is skipped and existing identifier meta is preserved.
    }
  }

  @JsonApiResource(type = "articles")
  public record ScalarSerializedMetaArticle(
      @JsonApiId String id,
      @JsonApiRelationship RelationshipLinkage<ResourceIdentifier, ScalarIdMeta> author) {}

  @JsonSerialize(using = ScalarIdMetaSerializer.class)
  public record ScalarIdMeta(String role) {}

  public static final class ScalarIdMetaSerializer extends ValueSerializer<ScalarIdMeta> {
    @Override
    public void serialize(
        ScalarIdMeta value, JsonGenerator generator, SerializationContext context) {
      generator.writeString(value.role());
    }
  }

  @JsonApiResource(type = "articles")
  public record WrappedMappedArticle(
      @JsonApiId String id,
      @JsonApiRelationship
          RelationshipLinkage<LinkageMapperFixtures.FlatAuthor, AuthorIdMeta> author) {}

  @JsonApiResource(type = "articles")
  public record WrappedMappedSetArticle(
      @JsonApiId String id,
      @JsonApiRelationship
          Set<RelationshipLinkage<LinkageMapperFixtures.FlatAuthor, CommentIdMeta>> comments) {}
}

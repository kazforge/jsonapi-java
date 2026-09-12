package com.kazforge.jsonapi.jackson3;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * Jackson-feature probe models owned by {@code ResourceMappingJacksonFeaturesSpec}: custom value
 * serializers and creator-based immutable beans.
 */
public final class JacksonFeatureFixtures {

  private JacksonFeatureFixtures() {}

  public static final class TitleSerializer extends ValueSerializer<FormattedTitle> {

    @Override
    public Class<FormattedTitle> handledType() {
      return FormattedTitle.class;
    }

    @Override
    public void serialize(FormattedTitle value, JsonGenerator gen, SerializationContext context)
        throws JacksonException {
      gen.writeString("[FORMATTED] " + value.text());
    }
  }

  @JsonSerialize(using = TitleSerializer.class)
  public record FormattedTitle(String text) {}

  @JsonApiResource(type = "articles")
  public record ArticleWithFormattedTitle(
      @JsonApiId String id, @JsonApiAttribute FormattedTitle title) {}

  @JsonApiResource(type = "articles")
  public static final class CreatorBasedArticle {

    private final String id;
    private final String title;

    @JsonCreator
    public CreatorBasedArticle(
        @JsonProperty("id") @JsonApiId String id,
        @JsonProperty("title") @JsonApiAttribute String title) {
      this.id = id;
      this.title = title;
    }

    public String getId() {
      return id;
    }

    public String getTitle() {
      return title;
    }
  }
}

package io.github.kazemek.jsonapi.jackson2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.github.kazemek.jsonapi.annotation.JsonApiAttribute;
import io.github.kazemek.jsonapi.annotation.JsonApiId;
import io.github.kazemek.jsonapi.annotation.JsonApiResource;
import java.io.IOException;

/**
 * Jackson-feature probe models owned by {@code ResourceMappingJacksonFeaturesSpec}: custom value
 * serializers and creator-based immutable beans. Adapter-local Jackson 2 copy of the shared shape.
 */
public final class JacksonFeatureFixtures {

  private JacksonFeatureFixtures() {}

  public static final class TitleSerializer extends JsonSerializer<FormattedTitle> {

    @Override
    public Class<FormattedTitle> handledType() {
      return FormattedTitle.class;
    }

    @Override
    public void serialize(FormattedTitle value, JsonGenerator gen, SerializerProvider context)
        throws IOException {
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

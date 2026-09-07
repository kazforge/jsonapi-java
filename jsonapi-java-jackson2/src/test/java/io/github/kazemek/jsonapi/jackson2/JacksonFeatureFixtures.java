package io.github.kazemek.jsonapi.jackson2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.util.NameTransformer;
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

  /** Object-declared unwrapping property whose runtime value is a POJO (raw-value dynamic path). */
  @JsonApiResource(type = "unwrapped-things")
  public static final class UnwrappedObjectThing {

    @JsonApiId public String id;

    @JsonApiAttribute
    @JsonUnwrapped(prefix = "nested_")
    public Object details;

    public UnwrappedObjectThing(String id, Object details) {
      this.id = id;
      this.details = details;
    }
  }

  /** Flattened value for the unwrapping probes. */
  @SuppressWarnings("unused")
  public static final class UnwrappedNestedValue {
    public String inner = "iv";
  }

  /** Unwrapping property supplied by a custom introspector, without {@code @JsonUnwrapped}. */
  @JsonApiResource(type = "custom-unwrapped-things")
  public static final class CustomUnwrappedThing {

    @JsonApiId public String id;

    @JsonApiAttribute public Object details;

    public CustomUnwrappedThing(String id, Object details) {
      this.id = id;
      this.details = details;
    }
  }

  /**
   * Supplies an unwrapping name transformer for {@link CustomUnwrappedThing} members without any
   * {@code @JsonUnwrapped} annotation, proving the raw-value writer honors the configured
   * introspector rather than only the annotation.
   */
  public static final class CustomUnwrappingIntrospector extends AnnotationIntrospector {

    @Override
    public Version version() {
      return Version.unknownVersion();
    }

    @Override
    public NameTransformer findUnwrappingNameTransformer(AnnotatedMember member) {
      return member.getDeclaringClass() == CustomUnwrappedThing.class
          ? NameTransformer.simpleTransformer("c_", null)
          : null;
    }
  }
}

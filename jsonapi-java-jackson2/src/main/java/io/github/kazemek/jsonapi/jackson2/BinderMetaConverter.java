package io.github.kazemek.jsonapi.jackson2;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.kazemek.jsonapi.core.model.Meta;
import java.util.Objects;

/**
 * {@link JsonApiDomainDocument.MetaConverter} implementation over the domain reader's derived
 * binder mapper.
 *
 * <p>One instance is created per {@link JsonApiDomainDocumentReader} and shared by all envelopes it
 * produces; {@code metaAs} therefore converts with the exact mapper configuration that bound the
 * document without the envelope retaining the mapper itself.
 */
final class BinderMetaConverter implements JsonApiDomainDocument.MetaConverter {

  private final JsonMapper binderMapper;

  BinderMetaConverter(JsonMapper binderMapper) {
    this.binderMapper = Objects.requireNonNull(binderMapper, "binderMapper");
  }

  @Override
  public Object convert(Meta meta, JavaType targetType) {
    return binderMapper.convertValue(meta.members(), targetType);
  }

  @Override
  public Object convert(Meta meta, Class<?> rawType) {
    return binderMapper.convertValue(meta.members(), binderMapper.constructType(rawType));
  }
}

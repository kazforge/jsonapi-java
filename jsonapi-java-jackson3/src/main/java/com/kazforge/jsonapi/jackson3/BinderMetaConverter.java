package com.kazforge.jsonapi.jackson3;

import com.kazforge.jsonapi.core.model.Meta;
import java.util.Objects;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link JsonApiDomainDocument.MetaConverter} implementation over the domain reader's derived
 * binder mapper.
 *
 * <p>One instance is created per {@link JsonApiDomainDocumentReader} and shared by all envelopes it
 * produces; {@code metaAs} therefore converts with the exact mapper configuration that bound the
 * document (ADR-004) without the envelope retaining the {@link JsonMapper} itself.
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

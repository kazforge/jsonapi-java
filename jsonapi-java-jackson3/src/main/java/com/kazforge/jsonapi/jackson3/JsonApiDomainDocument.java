package com.kazforge.jsonapi.jackson3;

import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.jackson.diagnostic.MappingLocation;
import com.kazforge.jsonapi.jackson.mapping.DomainData;
import com.kazforge.jsonapi.jackson.mapping.IncludedResources;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;

/**
 * Immutable, validated, domain-facing JSON:API document envelope with flat primary DTOs and
 * independently bound {@code included} DTOs.
 *
 * <p>Construct instances via {@link JsonApiDomainDocumentReader#readValue} or {@link
 * JsonApiDomainDocumentReader#fromDocument}; there is no public constructor because {@link
 * #metaAs(Class)} and {@link #metaAs(JavaType)} require the reader-derived meta conversion retained
 * at construction. Component nullness mirrors {@link
 * com.kazforge.jsonapi.core.model.JsonApiDocument}: Java {@code null} means the member is absent,
 * {@link DomainData.NullData} means explicit JSON {@code null}, and a present-empty {@code
 * included: []} yields a non-null empty {@link IncludedResources}. Collections are defensively
 * copied at construction and exposed as unmodifiable views.
 */
public final class JsonApiDomainDocument {

  private final Components components;
  private final MetaConverter metaConverter;

  JsonApiDomainDocument(Components components, MetaConverter metaConverter) {
    this.components = Objects.requireNonNull(components, "components");
    this.metaConverter = Objects.requireNonNull(metaConverter, "metaConverter");
  }

  /** Primary data; {@code null} when the {@code data} member is absent. */
  public @Nullable DomainData data() {
    return components.data();
  }

  /** Error objects; unmodifiable view of a defensive copy. */
  public @Nullable List<ErrorObject> errors() {
    return components.errors();
  }

  /** Document-level metadata; the already-decoded core value. */
  public @Nullable Meta meta() {
    return components.meta();
  }

  /** JSON:API object; the already-decoded core value. */
  public @Nullable JsonApiObject jsonapi() {
    return components.jsonapi();
  }

  /** Document-level links; the already-decoded core value. */
  public @Nullable Links links() {
    return components.links();
  }

  /** Bound included DTOs; {@code null} when the {@code included} member is absent. */
  public @Nullable IncludedResources included() {
    return components.included();
  }

  /** Additional document members in insertion order; unmodifiable view of a defensive copy. */
  public Map<String, @Nullable Object> additionalMembers() {
    return components.additionalMembers();
  }

  /**
   * Converts {@link #meta()} members to the given target type using the domain reader's derived
   * binder mapper (the same mapper that bound the document), or returns {@code null} when {@code
   * meta} is absent.
   *
   * @throws JsonApiMappingException with {@link MappingDiagnostic#UNSUPPORTED_ATTRIBUTE_VALUE} at
   *     {@code /meta} when conversion fails
   */
  public <T> @Nullable T metaAs(Class<T> targetType) {
    Objects.requireNonNull(targetType, "targetType");
    Object converted = convertMeta(targetType, meta -> metaConverter.convert(meta, targetType));
    if (converted == null) {
      return null;
    }
    return targetType.cast(converted);
  }

  /**
   * Converts {@link #meta()} members to the given Java type using the domain reader's derived
   * binder mapper, or returns {@code null} when {@code meta} is absent.
   *
   * @throws JsonApiMappingException with {@link MappingDiagnostic#UNSUPPORTED_ATTRIBUTE_VALUE} at
   *     {@code /meta} when conversion fails
   */
  public @Nullable Object metaAs(JavaType targetType) {
    Objects.requireNonNull(targetType, "targetType");
    return convertMeta(targetType, meta -> metaConverter.convert(meta, targetType));
  }

  private @Nullable Object convertMeta(Object targetType, Function<Meta, Object> conversion) {
    if (components.meta() == null) {
      return null;
    }
    try {
      return conversion.apply(components.meta());
    } catch (RuntimeException ex) {
      throw new JsonApiMappingException(
          MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE,
          null,
          MappingLocation.of("meta"),
          "Failed to convert meta members to " + targetType,
          ex);
    }
  }

  /**
   * Package-private conversion seam for {@link #metaAs(Class)} / {@link #metaAs(JavaType)}: applies
   * the domain reader's derived binder mapper to {@link Meta#members()} without retaining the
   * mapper itself on the envelope.
   */
  interface MetaConverter {

    Object convert(Meta meta, JavaType targetType);

    Object convert(Meta meta, Class<?> rawType);
  }

  /**
   * Package-private document member payload with the same absence/null rules as {@link
   * com.kazforge.jsonapi.core.model.JsonApiDocument}; the envelope copies and freezes it at
   * construction.
   */
  record Components(
      @Nullable DomainData data,
      @Nullable List<ErrorObject> errors,
      @Nullable Meta meta,
      @Nullable JsonApiObject jsonapi,
      @Nullable Links links,
      @Nullable IncludedResources included,
      Map<String, @Nullable Object> additionalMembers) {

    Components(
        @Nullable DomainData data,
        @Nullable List<ErrorObject> errors,
        @Nullable Meta meta,
        @Nullable JsonApiObject jsonapi,
        @Nullable Links links,
        @Nullable IncludedResources included,
        Map<String, @Nullable Object> additionalMembers) {
      this.data = data;
      this.errors = errors == null ? null : List.copyOf(errors);
      this.meta = meta;
      this.jsonapi = jsonapi;
      this.links = links;
      this.included = included;
      this.additionalMembers = copyAdditionalMembers(additionalMembers);
    }

    private static Map<String, @Nullable Object> copyAdditionalMembers(
        Map<String, @Nullable Object> members) {
      return Collections.unmodifiableMap(
          new LinkedHashMap<String, @Nullable Object>(Objects.requireNonNull(members, "members")));
    }
  }
}

package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.kazforge.jsonapi.jackson.internal.patch.PresenceMarker;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;
import java.io.IOException;
import org.jspecify.annotations.Nullable;

/**
 * Minimal contextual deserializer that reconstructs {@link PatchPresence} from an internal {@link
 * PresenceMarker} object.
 *
 * <p>{@link #createContextual} captures the property's single {@code PatchPresence} type argument
 * as the inner {@link JavaType}. {@code present=false} yields {@link PatchPresence#omitted()};
 * {@code present=true} yields {@link PatchPresence#present(Object)} with the {@code value} member
 * read through the inner type. When the {@code value} member is absent or is JSON {@code null}, the
 * inner type's null value is used (for example {@code Optional.empty()} for an {@link
 * java.util.Optional} inner), so the tri-state never collapses under caller serialization
 * configuration.
 *
 * <p>The marker shape is strictly enforced: any input that is not an internal marker object with a
 * boolean {@code present} member (and only the {@code present}/{@code value} members) fails loudly
 * instead of silently reconstructing {@code Omitted()}.
 */
final class PatchPresenceDeserializer extends JsonDeserializer<PatchPresence<?>>
    implements ContextualDeserializer {

  private static final String PRESENT = "present";
  private static final String VALUE = "value";

  private final @Nullable JavaType inner;

  PatchPresenceDeserializer() {
    this.inner = null;
  }

  private PatchPresenceDeserializer(JavaType inner) {
    this.inner = inner;
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
    JavaType contextualType = ctxt.getContextualType();
    JavaType resolvedInner =
        contextualType != null && contextualType.containedTypeCount() == 1
            ? contextualType.containedType(0)
            : ctxt.constructType(Object.class);
    return new PatchPresenceDeserializer(resolvedInner);
  }

  @Override
  public PatchPresence<?> deserialize(JsonParser parser, DeserializationContext ctxt)
      throws IOException {
    JavaType innerType = inner != null ? inner : ctxt.constructType(Object.class);
    MarkerFields fields = readMarker(parser, ctxt, innerType);
    if (!fields.present()) {
      return PatchPresence.omitted();
    }
    if (!fields.sawValue()) {
      return PatchPresence.present(nullValue(ctxt, innerType));
    }
    return PatchPresence.present(fields.value());
  }

  private static MarkerFields readMarker(
      JsonParser parser, DeserializationContext ctxt, JavaType innerType) throws IOException {
    if (!parser.isExpectedStartObjectToken()) {
      return invalidMarker(ctxt, innerType, "expected an internal presence marker object");
    }
    boolean sawPresent = false;
    boolean present = false;
    boolean sawValue = false;
    Object rawValue = null;
    JsonToken token = parser.nextToken();
    while (token != JsonToken.END_OBJECT) {
      if (token != JsonToken.FIELD_NAME) {
        return invalidMarker(ctxt, innerType, "expected a marker member name");
      }
      String name = parser.currentName();
      parser.nextToken();
      if (PRESENT.equals(name)) {
        if (parser.currentToken() != JsonToken.VALUE_TRUE
            && parser.currentToken() != JsonToken.VALUE_FALSE) {
          return invalidMarker(ctxt, innerType, "'present' must be a boolean");
        }
        present = parser.getBooleanValue();
        sawPresent = true;
      } else if (VALUE.equals(name)) {
        sawValue = true;
        rawValue = readValueMember(parser, ctxt, innerType);
      } else {
        return invalidMarker(ctxt, innerType, "unexpected member '" + name + "'");
      }
      token = parser.nextToken();
    }
    if (!sawPresent) {
      return invalidMarker(ctxt, innerType, "missing 'present' member");
    }
    return new MarkerFields(present, sawValue, rawValue);
  }

  private static @Nullable Object readValueMember(
      JsonParser parser, DeserializationContext context, JavaType innerType) throws IOException {
    if (parser.currentToken() == JsonToken.VALUE_NULL) {
      return nullValue(context, innerType);
    }
    return context.readValue(parser, innerType);
  }

  private static <T> T invalidMarker(
      DeserializationContext context, JavaType innerType, String detail)
      throws JsonMappingException {
    return context.reportInputMismatch(
        innerType, "Invalid internal PatchPresence marker: " + detail);
  }

  // getNullValue returns Java null for ordinary scalar inners (e.g. String), which the
  // PATCH tri-state relies on to distinguish present(null) from omitted(); the analyzer does not
  // model that nullability.
  @SuppressWarnings("DataFlowIssue")
  private static @Nullable Object nullValue(DeserializationContext context, JavaType innerType)
      throws JsonMappingException {
    JsonDeserializer<Object> deserializer = context.findRootValueDeserializer(innerType);
    if (deserializer == null) {
      throw JsonMappingException.from(
          context.getParser(),
          "Cannot deserialize the inner type of PatchPresence: " + innerType.toCanonical());
    }
    return deserializer.getNullValue(context);
  }

  private record MarkerFields(boolean present, boolean sawValue, @Nullable Object value) {}
}

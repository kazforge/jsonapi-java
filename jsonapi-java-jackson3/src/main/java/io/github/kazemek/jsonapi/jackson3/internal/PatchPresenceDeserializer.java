package io.github.kazemek.jsonapi.jackson3.internal;

import io.github.kazemek.jsonapi.jackson.internal.patch.PresenceMarker;
import io.github.kazemek.jsonapi.jackson.patch.PatchPresence;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.exc.InvalidDefinitionException;

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
final class PatchPresenceDeserializer extends ValueDeserializer<PatchPresence<?>> {

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
  public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
    JavaType contextualType = ctxt.getContextualType();
    JavaType resolvedInner =
        contextualType != null && contextualType.containedTypeCount() == 1
            ? contextualType.containedType(0)
            : ctxt.constructType(Object.class);
    return new PatchPresenceDeserializer(resolvedInner);
  }

  @Override
  public PatchPresence<?> deserialize(JsonParser parser, DeserializationContext ctxt) {
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
      JsonParser parser, DeserializationContext ctxt, JavaType innerType) {
    if (!parser.isExpectedStartObjectToken()) {
      invalidMarker(ctxt, innerType, "expected an internal presence marker object");
    }
    boolean sawPresent = false;
    boolean present = false;
    boolean sawValue = false;
    Object rawValue = null;
    JsonToken token = parser.nextToken();
    while (token != JsonToken.END_OBJECT) {
      String name = parser.currentName();
      parser.nextToken();
      if (PRESENT.equals(name)) {
        if (parser.currentToken() != JsonToken.VALUE_TRUE
            && parser.currentToken() != JsonToken.VALUE_FALSE) {
          invalidMarker(ctxt, innerType, "'present' must be a boolean");
        }
        present = parser.getBooleanValue();
        sawPresent = true;
      } else if (VALUE.equals(name)) {
        sawValue = true;
        rawValue = readValueMember(parser, ctxt, innerType);
      } else {
        invalidMarker(ctxt, innerType, "unexpected member '" + name + "'");
      }
      token = parser.nextToken();
    }
    if (!sawPresent) {
      invalidMarker(ctxt, innerType, "missing 'present' member");
    }
    return new MarkerFields(present, sawValue, rawValue);
  }

  private static @Nullable Object readValueMember(
      JsonParser parser, DeserializationContext context, JavaType innerType) {
    if (parser.currentToken() == JsonToken.VALUE_NULL) {
      return nullValue(context, innerType);
    }
    return context.readValue(parser, innerType);
  }

  private static void invalidMarker(
      DeserializationContext context, JavaType innerType, String detail) {
    context.reportInputMismatch(innerType, "Invalid internal PatchPresence marker: " + detail);
  }

  private static @Nullable Object nullValue(DeserializationContext context, JavaType innerType) {
    ValueDeserializer<Object> deserializer = context.findRootValueDeserializer(innerType);
    if (deserializer == null) {
      throw InvalidDefinitionException.from(
          (JsonParser) null,
          "Cannot deserialize the inner type of PatchPresence: " + innerType.toCanonical(),
          innerType);
    }
    return deserializer.getNullValue(context);
  }

  private record MarkerFields(boolean present, boolean sawValue, @Nullable Object value) {}
}

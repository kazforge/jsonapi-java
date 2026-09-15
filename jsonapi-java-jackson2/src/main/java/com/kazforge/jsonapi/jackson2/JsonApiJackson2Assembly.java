package com.kazforge.jsonapi.jackson2;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.ser.DefaultSerializerProvider;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.kazforge.jsonapi.jackson2.internal.MetaBindingModule;
import com.kazforge.jsonapi.jackson2.internal.codec.JsonApiDocumentModule;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Package-private Jackson 2 mapper assembly authority shared by the public capability factories,
 * the Level-1 runtime composition, and the typed domain envelope reader.
 *
 * <p>Centralizes configured-mapper derivation so composition flows one way from public composition
 * into implementation without calling back through the public factory facade.
 */
final class JsonApiJackson2Assembly {

  private JsonApiJackson2Assembly() {}

  /**
   * Returns {@code true} when the caller's configured mapper already serializes a present {@code
   * Optional} without the adapter's fallback (JDK 8 datatype module, a custom serializer, or a
   * relaxed bean-serialization configuration). Any failure counts as missing support.
   */
  static boolean supportsOptionalSerialization(JsonMapper mapper) {
    try (TokenBuffer buffer = new TokenBuffer(mapper, false)) {
      ((DefaultSerializerProvider) mapper.getSerializerProviderInstance())
          .serializeValue(buffer, Optional.of("probe"));
      return true;
    } catch (RuntimeException | IOException e) {
      return false;
    }
  }

  /**
   * Returns {@code true} when the caller's configured mapper already deserializes a present {@code
   * Optional} without the adapter's fallback (JDK 8 datatype module or a custom deserializer). Any
   * failure counts as missing support.
   */
  static boolean supportsOptionalDeserialization(JsonMapper mapper) {
    JavaType type = mapper.constructType(new TypeReference<Optional<String>>() {});
    try (TokenBuffer buffer = new TokenBuffer(mapper, false)) {
      buffer.writeString("probe");
      try (JsonParser parser = buffer.asParser(mapper)) {
        mapper.readValue(parser, type);
        return true;
      }
    } catch (RuntimeException | IOException e) {
      return false;
    }
  }

  /**
   * Derives a mapper for resource mapping introspection and attribute/meta conversion. Registers
   * {@link MetaBindingModule} so built-in {@code ResourceIdentifier} values can round-trip
   * identifier meta. When the caller's configuration cannot serialize a present {@code Optional},
   * the derived mapping mapper also registers the pinned JDK 8 datatype module so the supported
   * cross-major Optional contract holds on the default configured-mapper path; caller-supplied
   * Optional serialization is detected behaviorally and always wins. Does not register the JSON:API
   * document module because the resource mapper produces core model objects, not serialized output.
   */
  static JsonMapper resourceMappingMapper(JsonMapper base) {
    JsonMapper.Builder derived = base.rebuild().addModule(new MetaBindingModule());
    if (!supportsOptionalSerialization(base)) {
      derived = derived.addModule(new Jdk8Module());
    }
    return derived.build();
  }

  /**
   * Derives a mapper for resource mapping introspection, identifier conversion, and flat binder
   * construction. Registers {@link MetaBindingModule} so built-in {@code ResourceIdentifier} values
   * can round-trip identifier meta. When the caller's configuration cannot deserialize a present
   * {@code Optional}, the derived binder mapper also registers the pinned JDK 8 datatype module so
   * the supported cross-major Optional contract holds on the default configured-mapper path;
   * caller-supplied Optional handling is detected behaviorally and always wins. Does not register
   * the JSON:API document module because the binder produces application values, not serialized
   * output.
   */
  static JsonMapper resourceBindingMapper(JsonMapper base) {
    JsonMapper.Builder derived = base.rebuild().addModule(new MetaBindingModule());
    if (!supportsOptionalDeserialization(base)) {
      derived = derived.addModule(new Jdk8Module());
    }
    return derived.build();
  }

  /**
   * Derives a new mapper with JSON:API document serializers registered. Package-private so callers
   * cannot serialize documents without aggregate validation.
   */
  static JsonMapper documentMapper(JsonMapper base) {
    Objects.requireNonNull(base, "base");
    return base.rebuild().addModule(new JsonApiDocumentModule()).build();
  }
}

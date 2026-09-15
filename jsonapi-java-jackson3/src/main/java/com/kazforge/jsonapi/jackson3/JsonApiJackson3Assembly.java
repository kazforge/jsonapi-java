package com.kazforge.jsonapi.jackson3;

import com.kazforge.jsonapi.jackson3.internal.MetaBindingModule;
import com.kazforge.jsonapi.jackson3.internal.codec.JsonApiDocumentModule;
import java.util.Objects;
import tools.jackson.databind.json.JsonMapper;

/**
 * Package-private Jackson 3 mapper assembly authority shared by the public capability factories,
 * the Level-1 runtime composition, and the typed domain envelope reader.
 *
 * <p>Centralizes configured-mapper derivation so composition flows one way from public composition
 * into implementation without calling back through the public factory facade.
 */
final class JsonApiJackson3Assembly {

  private JsonApiJackson3Assembly() {}

  /**
   * Derives a mapper for resource mapping introspection, attribute conversion, and binder
   * construction. Registers {@link MetaBindingModule} so built-in {@code ResourceIdentifier} values
   * can round-trip identifier meta. Does not register the JSON:API document module because the
   * resource mapper produces core model objects, not serialized output.
   */
  static JsonMapper resourceMappingMapper(JsonMapper base) {
    return base.rebuild().addModule(new MetaBindingModule()).build();
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

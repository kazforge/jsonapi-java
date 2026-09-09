package io.github.kazemek.jsonapi.jackson3.internal;

import io.github.kazemek.jsonapi.jackson.internal.patch.PresenceMarker;
import io.github.kazemek.jsonapi.jackson.patch.PatchPresence;
import tools.jackson.databind.module.SimpleModule;

/**
 * Registers the minimal internal {@link PatchPresence} deserializer and the deterministic {@link
 * PresenceMarker} serializer on the derived binder mapper used by the typed PATCH DTO path. The
 * caller's mapper is never mutated.
 */
public final class PatchPresenceModule extends SimpleModule {

  public PatchPresenceModule() {
    super("jsonapi-java-patch-presence");
    addDeserializer(PatchPresence.class, new PatchPresenceDeserializer());
    addSerializer(PresenceMarker.class, PresenceMarkerSerializer.INSTANCE);
  }
}

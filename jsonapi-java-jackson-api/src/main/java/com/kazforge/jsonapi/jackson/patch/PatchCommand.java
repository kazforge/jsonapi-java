package com.kazforge.jsonapi.jackson.patch;

import java.util.List;
import java.util.Objects;

/**
 * Immutable presence-aware resource-update command parameterized by the annotated DTO type {@code
 * T}.
 *
 * <p>Carries the converted DTO identifier and exactly the supplied mapped attribute and
 * relationship changes. Does not construct a complete DTO, resolve {@code included}, or mutate
 * domain state — applications authorize and apply the command.
 */
public record PatchCommand<T>(Class<T> resourceType, Object identity, List<PatchChange> changes) {

  public PatchCommand {
    Objects.requireNonNull(resourceType, "resourceType");
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(changes, "changes");
    for (PatchChange change : changes) {
      Objects.requireNonNull(change, "changes element");
    }
    changes = List.copyOf(changes);
  }
}

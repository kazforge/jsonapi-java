package com.kazforge.jsonapi.fixtures.contract;

import com.kazforge.jsonapi.mapping.ResourceDecoration;
import com.kazforge.jsonapi.mapping.ResourceDecorator;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * Passive {@link ResourceDecorator} carrier that returns one configured decoration for any
 * resource. The shared decoration characterization contract uses it to supply a registry without
 * Groovy closure support classes, keeping the contract-fixture dependency surface
 * application-shaped.
 *
 * @param <T> the application domain type the decoration applies to
 */
@NullMarked
public final class FixedResourceDecorator<T> implements ResourceDecorator<T> {

  private final ResourceDecoration decoration;

  public FixedResourceDecorator(ResourceDecoration decoration) {
    this.decoration = Objects.requireNonNull(decoration, "decoration");
  }

  @Override
  public ResourceDecoration decorate(T resource) {
    return decoration;
  }
}

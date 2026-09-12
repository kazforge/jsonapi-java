package com.kazforge.jsonapi.jackson.mapping;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Immutable registry of {@link ResourceDecorator} instances keyed by application domain type.
 *
 * <p>Lookup is exact-match on the effective runtime type's raw class; subclasses require their own
 * registration. The registry is safe for concurrent use once created. It is not a global mutable
 * registry — supply it through mapper construction.
 *
 * <p>Registered decorators are stored by reference and reused for every mapping call. When a mapper
 * created from this registry is shared across threads, each decorator must be safe for concurrent
 * invocation.
 *
 * <p>Resolution is deterministic: at most one decorator per raw class, no prefix or type-string
 * indirection, and no dependence on JSON:API type strings.
 */
public final class ResourceDecoratorRegistry {

  private static final ResourceDecoratorRegistry EMPTY = new ResourceDecoratorRegistry(Map.of());

  private final Map<Class<?>, ResourceDecorator<?>> decorators;

  private ResourceDecoratorRegistry(Map<Class<?>, ResourceDecorator<?>> decorators) {
    Map<Class<?>, ResourceDecorator<?>> copy = new LinkedHashMap<>();
    for (Map.Entry<Class<?>, ResourceDecorator<?>> entry : decorators.entrySet()) {
      Class<?> key = Objects.requireNonNull(entry.getKey(), "type");
      ResourceDecorator<?> value = Objects.requireNonNull(entry.getValue(), "decorator for " + key);
      copy.put(key, value);
    }
    this.decorators = Collections.unmodifiableMap(copy);
  }

  /** Returns an empty registry (no decorators). */
  public static ResourceDecoratorRegistry empty() {
    return EMPTY;
  }

  /** Returns a registry copied from the given map (defensive copy, unmodifiable). */
  public static ResourceDecoratorRegistry of(Map<Class<?>, ResourceDecorator<?>> decorators) {
    Objects.requireNonNull(decorators, "decorators");
    if (decorators.isEmpty()) {
      return empty();
    }
    return new ResourceDecoratorRegistry(decorators);
  }

  /** Creates a builder for one immutable registry. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the decorator for the given raw domain class, if any, via an exact match on raw class.
   */
  @SuppressWarnings("unchecked")
  public <T> @Nullable ResourceDecorator<T> decoratorFor(Class<T> type) {
    Objects.requireNonNull(type, "type");
    return (ResourceDecorator<T>) decorators.get(type);
  }

  /** Returns the number of registered decorators. */
  public int size() {
    return decorators.size();
  }

  /** Returns {@code true} when no decorators are registered. */
  public boolean isEmpty() {
    return decorators.isEmpty();
  }

  /** Returns an unmodifiable view of the underlying map. */
  public Map<Class<?>, ResourceDecorator<?>> asMap() {
    return decorators;
  }

  /** Mutable builder whose {@link #build()} result defensively copies supplied values. */
  public static final class Builder {

    private final Map<Class<?>, ResourceDecorator<?>> decorators = new LinkedHashMap<>();

    private Builder() {}

    /**
     * Registers a decorator for the given domain class.
     *
     * @param type the domain class (exact raw-class match at lookup time)
     * @param decorator the decorator for that type
     * @param <T> domain type
     */
    public <T> Builder register(Class<T> type, ResourceDecorator<? super T> decorator) {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(decorator, "decorator");
      if (decorators.containsKey(type)) {
        throw new IllegalArgumentException("Duplicate decorator for " + type.getName());
      }
      decorators.put(type, decorator);
      return this;
    }

    /** Builds an immutable registry. */
    public ResourceDecoratorRegistry build() {
      if (decorators.isEmpty()) {
        return empty();
      }
      return new ResourceDecoratorRegistry(decorators);
    }
  }
}

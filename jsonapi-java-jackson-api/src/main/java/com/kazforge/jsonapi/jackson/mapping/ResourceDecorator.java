package com.kazforge.jsonapi.jackson.mapping;

/**
 * Application-provided decoration for JSON:API resource links during domain writes.
 *
 * <p>Decorators are application/runtime collaborators, not domain-model metadata. They are supplied
 * through mapper construction so dependency-injected, request-aware, or tenant-aware link builders
 * remain possible without the library knowing how dependencies are produced. They are not declared
 * on JSON:API annotations.
 *
 * <p>A decorator may contribute only:
 *
 * <ul>
 *   <li>{@code ResourceObject.links}, and
 *   <li>{@code Relationship.links} for already-mapped relationships
 * </ul>
 *
 * All other resource semantics — type, id/lid, attributes, relationship data/linkage, resource
 * meta, relationship meta, identifier meta, included membership, sparse-fieldset provenance, and
 * document-level links — remain owned by normal mapping.
 *
 * <p>Relationship decoration is keyed by the mapped property <em>identity</em>, i.e. the Jackson
 * logical property name (the internal name derived from the Java field/record component/getter,
 * e.g. {@code "comments"}). The mapper resolves that identity through the configured Jackson
 * external name (for example {@code @JsonProperty("article-comments")}) and applies the decoration
 * under the final wire member name automatically. Decorators must not repeat the wire name.
 *
 * <p>Returning {@code null} or a decoration containing {@code null} keys/values is invalid and
 * fails with a stable mapping diagnostic. Unknown or non-relationship targets also fail
 * deterministically. An empty decoration ({@link ResourceDecoration#empty()}) means no decoration.
 *
 * <p>Decorators are invoked during mapping and may be called concurrently when a {@code
 * JsonApiResourceMapper} is shared. A decorator supplied to a shared mapper must be safe for
 * concurrent invocation; the mapper and its {@link ResourceDecoratorRegistry} do not provide
 * per-call isolation.
 *
 * @param <T> the application domain type
 */
@FunctionalInterface
public interface ResourceDecorator<T> {

  /**
   * Returns decoration for the given resource instance, or {@link ResourceDecoration#empty()} when
   * no links are needed. Must not return {@code null}.
   */
  ResourceDecoration decorate(T resource);
}

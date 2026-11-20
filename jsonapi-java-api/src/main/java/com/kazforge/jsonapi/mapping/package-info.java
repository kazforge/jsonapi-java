/**
 * Backend-independent application/domain mapping contracts and values.
 *
 * <p>JSON:API mapping roles select protocol locations without replacing the backend's property
 * configuration. Identifier conversion, resource-type registrations, and resource decorators are
 * explicit inputs to major-specific mapper or reader construction; there is no global registry or
 * ambient mapping configuration.
 *
 * <p>{@link com.kazforge.jsonapi.mapping.MappedDocument} carries mapping-produced sparse-fieldset
 * provenance to the document writer so validation observes the representation that was actually
 * mapped. {@link com.kazforge.jsonapi.mapping.DomainData} and {@link
 * com.kazforge.jsonapi.mapping.IncludedResources} preserve document shape and wire order for
 * advanced heterogeneous envelopes. {@link com.kazforge.jsonapi.mapping.RelationshipLinkage} is the
 * opt-in carrier for meta owned by one relationship identifier occurrence.
 *
 * <p>{@link com.kazforge.jsonapi.mapping.BoundResource} carries a successfully bound primary DTO
 * together with immutable request-local source lookup. {@link
 * com.kazforge.jsonapi.mapping.BindingSource} distinguishes known omission, explicit wire null,
 * supplied non-null values, and unresolved attribution. Only supplied values carry exact
 * document-relative locations. This is input attribution, not validation, update policy, or
 * construction-failure path translation.
 */
@NullMarked
package com.kazforge.jsonapi.mapping;

import org.jspecify.annotations.NullMarked;

/**
 * Major-neutral application/domain mapping contracts and values.
 *
 * <p>Configured Jackson remains the authority for property discovery, visibility, wire names,
 * creators, and ordinary Java conversion. JSON:API mapping roles select protocol locations without
 * replacing that property configuration. Identifier conversion, resource-type registrations, and
 * resource decorators are explicit inputs to major-specific mapper or reader construction; there is
 * no global registry or ambient mapping configuration.
 *
 * <p>{@link com.kazforge.jsonapi.jackson.mapping.MappedDocument} carries mapping-produced
 * sparse-fieldset provenance to the document writer so validation observes the representation that
 * was actually mapped. {@link com.kazforge.jsonapi.jackson.mapping.DomainData} and {@link
 * com.kazforge.jsonapi.jackson.mapping.IncludedResources} preserve document shape and wire order
 * for advanced heterogeneous envelopes. {@link
 * com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage} is the opt-in carrier for meta owned by
 * one relationship identifier occurrence.
 */
@NullMarked
package com.kazforge.jsonapi.jackson.mapping;

import org.jspecify.annotations.NullMarked;

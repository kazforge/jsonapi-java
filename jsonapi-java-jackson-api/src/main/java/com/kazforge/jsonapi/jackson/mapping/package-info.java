/**
 * Application/domain mapping contracts.
 *
 * <p>Provides {@link com.kazforge.jsonapi.jackson.mapping.IdentifierConverter}, {@link
 * com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage}, {@link
 * com.kazforge.jsonapi.jackson.mapping.MappedDocument}, {@link
 * com.kazforge.jsonapi.jackson.mapping.DomainData}, {@link
 * com.kazforge.jsonapi.jackson.mapping.IncludedResources}, and resource-link decoration contracts
 * {@link com.kazforge.jsonapi.jackson.mapping.ResourceDecorator}, {@link
 * com.kazforge.jsonapi.jackson.mapping.ResourceDecoration}, {@link
 * com.kazforge.jsonapi.jackson.mapping.RelationshipDecoration}, and {@link
 * com.kazforge.jsonapi.jackson.mapping.ResourceDecoratorRegistry}. {@link
 * com.kazforge.jsonapi.jackson.mapping.ResourceTypeRegistry} provides explicit heterogeneous
 * resource-type dispatch without depending on a Jackson major.
 */
@NullMarked
package com.kazforge.jsonapi.jackson.mapping;

import org.jspecify.annotations.NullMarked;

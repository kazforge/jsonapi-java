/**
 * Application/domain mapping contracts.
 *
 * <p>Provides {@link io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter}, {@link
 * io.github.kazemek.jsonapi.jackson.mapping.RelationshipLinkage}, {@link
 * io.github.kazemek.jsonapi.jackson.mapping.MappedDocument}, {@link
 * io.github.kazemek.jsonapi.jackson.mapping.DomainData}, {@link
 * io.github.kazemek.jsonapi.jackson.mapping.IncludedResources}, and resource-link decoration
 * contracts {@link io.github.kazemek.jsonapi.jackson.mapping.ResourceDecorator}, {@link
 * io.github.kazemek.jsonapi.jackson.mapping.ResourceDecoration}, {@link
 * io.github.kazemek.jsonapi.jackson.mapping.RelationshipDecoration}, and {@link
 * io.github.kazemek.jsonapi.jackson.mapping.ResourceDecoratorRegistry}. {@link
 * io.github.kazemek.jsonapi.jackson.mapping.ResourceTypeRegistry} provides explicit heterogeneous
 * resource-type dispatch without depending on a Jackson major.
 */
@NullMarked
package io.github.kazemek.jsonapi.jackson.mapping;

import org.jspecify.annotations.NullMarked;

package com.kazforge.jsonapi.fixtures.domainread;

/**
 * Backend-neutral custom relationship target for the configured linkage-mapper read contracts. It
 * is not a built-in {@link com.kazforge.jsonapi.core.model.ResourceIdentifier} shape, so binding it
 * requires an adapter-registered {@code RelationshipLinkageMapper}.
 */
public record MappedReadTarget(String type, String id) {}

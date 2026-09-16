/**
 * Immutable JSON:API v1.1 document model that preserves wire-visible states.
 *
 * <p>Java {@code null} on a containing component means a member is absent ({@link
 * org.jspecify.annotations.Nullable}). Sealed value types such as {@link
 * com.kazforge.jsonapi.core.model.DocumentData} and {@link
 * com.kazforge.jsonapi.core.model.RelationshipData} represent explicit JSON {@code null}, single,
 * and collection forms. Additional members hold pass-through extension and {@code @} names without
 * treating them as attributes, relationships, or links. {@link
 * com.kazforge.jsonapi.core.model.Links} additionally reserves context-standard link names out of
 * its {@code additionalMembers} map so those keys cannot hold open JSON.
 *
 * <p>Local construction invariants are enforced here; identity uniqueness, full linkage, and other
 * rules requiring complete-document context are enforced separately by aggregate validation.
 */
@NullMarked
package com.kazforge.jsonapi.core.model;

import org.jspecify.annotations.NullMarked;

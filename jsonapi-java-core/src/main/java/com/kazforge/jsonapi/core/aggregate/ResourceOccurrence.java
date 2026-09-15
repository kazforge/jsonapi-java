package com.kazforge.jsonapi.core.aggregate;

/**
 * Internal location of a resource object or resource identifier occurrence during aggregate
 * validation.
 *
 * <p>Operation semantics, endpoint role, and occurrence are independent axes. The validator passes
 * the unchanged public {@link ValidationContext} policy through traversal and carries this
 * occurrence only where resource identity and write rules need to distinguish primary data from
 * relationship linkage and included resources.
 */
enum ResourceOccurrence {
  /** A resource object or identifier in top-level primary data, including collection elements. */
  PRIMARY_DATA,
  /** An identifier in relationship linkage, either to-one or to-many. */
  RELATIONSHIP_LINKAGE,
  /** A resource object under top-level {@code included}. */
  INCLUDED_RESOURCE
}

package com.kazforge.jsonapi.core.validation;

/**
 * Declares what primary data represents at the endpoint being validated.
 *
 * <p>Endpoint semantics cannot always be inferred from the document value. In particular, explicit
 * null primary data can represent either an ordinary missing single resource or empty to-one
 * relationship linkage, so callers select the role explicitly. Single versus collection cardinality
 * remains represented by the sealed {@code DocumentData} variant and is not a second caller
 * declaration. Jackson decoding kind ({@code PrimaryDataKind}) remains a separate codec choice
 * about whether objects decode as resource objects or resource identifiers.
 */
public enum PrimaryDataContext {
  /**
   * Primary data represents an ordinary resource or resource collection. This also covers fetching
   * related resources through a related-resource URL; it does not mean relationship linkage.
   */
  RESOURCE,
  /**
   * Primary data represents resource linkage returned by or sent to a relationship endpoint:
   * explicit null, a single identifier, or an identifier collection.
   */
  RELATIONSHIP
}

/**
 * Passive application-shaped carriers for Jackson 3 characterization tests. Behavioral specs live
 * in the Jackson 3 test source set under {@code com.kazforge.jsonapi.jackson3.contract}; this
 * package contains only DTOs and application collaborators consumed by those specs.
 *
 * <p>Carriers may use Jackson annotations such as {@code @JsonProperty} to exercise configured
 * property naming. Runtime setup, scenario selection, assertions, and diagnostics belong to the
 * consuming tests.
 */
package com.kazforge.jsonapi.fixtures.contract;

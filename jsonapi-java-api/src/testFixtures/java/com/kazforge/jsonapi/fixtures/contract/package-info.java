/**
 * Shared backend-neutral characterization contracts for the observable mapping and binding
 * semantics that later extraction slices move. Abstract Spock specs in this package typically bind
 * to the Level-1 {@code JsonApi} contract; each adapter supplies concrete subclasses that provide
 * its configured runtime only, so Jackson 2 and Jackson 3 run the same observable contract.
 * Extraction slices that have no Level-1 entry point instead expose a narrow abstract hook: the
 * adapter subclass performs the native invocation and returns only neutral observable results or
 * failures. Scenarios and assertions stay shared; adapter mechanics stay in the concrete subclass.
 *
 * <p>Contract specs observe JSON:API member semantics, never backend mechanics. Fixture carriers in
 * this package may use Jackson-major-neutral annotations such as {@code @JsonProperty} purely as
 * test mechanics to reach backend rename scenarios; such annotations are not part of the
 * backend-neutral characterization contract and must not constrain future non-Jackson backends.
 * Assertions stay at the JSON:API member level, and JSON object member ordering is never frozen as
 * observable semantics.
 *
 * <p>Each contract test exercises one representative entry point with direct value assertions.
 * Scenario selection, expected-outcome interpretation, and adapter-specific diagnostics or
 * exception policies remain in adapter-owned tests. New or shared observable semantics for a later
 * extraction slice are first secured in this package for that slice, before ownership moves.
 */
package com.kazforge.jsonapi.fixtures.contract;

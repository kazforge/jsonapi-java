/**
 * Passive test fixtures and canonical JSON:API resources reused by Jackson 3 suites.
 *
 * <p>Fixture subpackages contain application-shaped carriers whose behavior is limited to what is
 * needed to represent or observe the shape under test, such as access-counting probes. {@link
 * TestFixtureResources} provides neutral classpath access to the canonical corpus and pinned
 * schemas. Adapter invocation, expected behavioral outcomes, assertions, scenario selection, and
 * test orchestration remain in adapter-owned tests.
 */
package com.kazforge.jsonapi.fixtures;

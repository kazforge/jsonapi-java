/**
 * Self-contained Jackson 3 JSON:API document codec: token-driven wire decoding, wire emission, and
 * document module registration. Not a public API surface.
 *
 * <p>Document, resource, error, and link readers decode through token primitives, object-member
 * iteration, open-value decoding, and shared meta decoding composed from above; nested readers
 * never call back into the top-level document orchestrator. The top-level document reader remains
 * the composition root for primary data, while open values recurse only through the dedicated
 * open-value owner. Public capability factories may depend on this package; this package depends
 * only on core, Jackson-major-neutral contracts and helpers, and Jackson 3.
 */
@NullMarked
package com.kazforge.jsonapi.jackson3.internal.codec;

import org.jspecify.annotations.NullMarked;

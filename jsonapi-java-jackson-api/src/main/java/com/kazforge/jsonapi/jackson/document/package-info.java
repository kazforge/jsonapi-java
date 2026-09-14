/**
 * Major-neutral JSON:API document read/write contract values.
 *
 * <p>Provides {@link com.kazforge.jsonapi.jackson.document.DocumentReadContext}, {@link
 * com.kazforge.jsonapi.jackson.document.PrimaryDataKind}, and {@link
 * com.kazforge.jsonapi.jackson.document.DocumentEnvelope}. {@code DocumentReadContext} carries an
 * aggregate {@code ValidationContext} (including the primary-data endpoint role) plus an explicit
 * decoding kind; endpoint semantics and decoding kind are independent axes.
 */
@NullMarked
package com.kazforge.jsonapi.jackson.document;

import org.jspecify.annotations.NullMarked;

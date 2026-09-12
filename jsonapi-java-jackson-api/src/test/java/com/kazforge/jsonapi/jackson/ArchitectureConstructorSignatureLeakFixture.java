package com.kazforge.jsonapi.jackson;

import com.kazforge.jsonapi.jackson.internal.ArchitectureInternalException;

/** Test-only supported-package type whose constructor leaks an internal exception. */
@SuppressWarnings("unused")
public final class ArchitectureConstructorSignatureLeakFixture {

  /** Declares an unsupported internal exception to exercise the architecture guard. */
  public ArchitectureConstructorSignatureLeakFixture() throws ArchitectureInternalException {
    throw new ArchitectureInternalException();
  }
}

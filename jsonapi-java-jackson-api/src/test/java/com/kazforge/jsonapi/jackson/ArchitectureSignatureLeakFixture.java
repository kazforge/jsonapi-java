package com.kazforge.jsonapi.jackson;

import com.kazforge.jsonapi.jackson.internal.ArchitectureInternalException;

/** Test-only supported-package type whose method leaks an internal exception. */
@SuppressWarnings("unused")
public final class ArchitectureSignatureLeakFixture {

  /** Declares an unsupported internal exception to exercise the architecture guard. */
  public void leaksInternalException() throws ArchitectureInternalException {
    throw new ArchitectureInternalException();
  }
}

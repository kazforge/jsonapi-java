package com.kazforge.jsonapi.jackson3;

import com.kazforge.jsonapi.jackson3.internal.codec.ArchitectureAdapterInternalException;

/** Test-only supported-package type whose method leaks an adapter-internal exception. */
@SuppressWarnings("unused")
public final class ArchitectureAdapterSignatureLeakFixture {

  /** Declares an unsupported adapter-internal exception to exercise the architecture guard. */
  public void leaksAdapterInternalException() throws ArchitectureAdapterInternalException {
    throw new ArchitectureAdapterInternalException();
  }
}

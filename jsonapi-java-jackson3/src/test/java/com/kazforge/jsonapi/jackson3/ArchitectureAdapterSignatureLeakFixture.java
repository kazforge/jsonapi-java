package com.kazforge.jsonapi.jackson3;

import com.kazforge.jsonapi.jackson3.internal.codec.ArchitectureAdapterInternalException;
import com.kazforge.jsonapi.mapping.internal.PropertyRole;
import java.util.List;

/** Test-only supported signatures exposing unsupported implementation types. */
@SuppressWarnings("unused")
public final class ArchitectureAdapterSignatureLeakFixture {

  public ArchitectureAdapterSignatureLeakFixture() throws ArchitectureAdapterInternalException {
    throw new ArchitectureAdapterInternalException();
  }

  /** Declares an unsupported adapter-internal exception to exercise the architecture guard. */
  public void leaksAdapterInternalException() throws ArchitectureAdapterInternalException {
    throw new ArchitectureAdapterInternalException();
  }

  public List<PropertyRole> leaksMappingInternalArgument() {
    return List.of(PropertyRole.ATTRIBUTE);
  }
}

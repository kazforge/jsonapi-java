package com.kazforge.jsonapi.patch

import spock.lang.Specification

class PatchPresenceSpec extends Specification {

  def "patch presence tri-state distinguishes omitted, present value, and present null"() {
    expect:
    PatchPresence.omitted().isOmitted()
    !PatchPresence.present("v").isOmitted()
    !PatchPresence.present(null).isOmitted()
    PatchPresence.present("v").value() == "v"
    PatchPresence.present(null).value() == null
  }

  def "patch presence states compare by value across record variants"() {
    expect:
    PatchPresence.omitted() == new PatchPresence.Omitted()
    PatchPresence.present("v") == new PatchPresence.Present("v")
    PatchPresence.present(null) == new PatchPresence.Present(null)
    PatchPresence.omitted() != PatchPresence.present(null)
    PatchPresence.present("v") != PatchPresence.present(null)
  }

  def "patch presence is exhaustively matchable over omitted and present"() {
    given:
    def inputs = [
      PatchPresence.omitted(),
      PatchPresence.present("v"),
      PatchPresence.present(null)
    ]

    expect:
    // The sealed contract must stay exactly the two variants exercised below; a newly permitted
    // implementation fails this assertion until it is deliberately handled in the switch.
    (Set) PatchPresence.class.getPermittedSubclasses().toSet() ==
        [
          PatchPresence.Omitted,
          PatchPresence.Present
        ].toSet()
    inputs.every { presence ->
      switch (presence) {
        case PatchPresence.Omitted:
          return presence.isOmitted()
        case PatchPresence.Present:
          return true
      }
    }
  }

  def "patch presence keeps nullable Optional as a separate inner concern"() {
    given:
    def omitted = PatchPresence.<Optional<String>>omitted()
    def empty = PatchPresence.present(Optional.empty())
    def nulled = PatchPresence.present(null)

    expect:
    omitted.isOmitted()
    !empty.isOmitted()
    !nulled.isOmitted()
    empty.value() == Optional.empty()
    nulled.value() == null
  }
}

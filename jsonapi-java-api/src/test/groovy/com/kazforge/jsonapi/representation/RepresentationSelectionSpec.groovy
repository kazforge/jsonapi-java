package com.kazforge.jsonapi.representation

import spock.lang.Specification

class RepresentationSelectionSpec extends Specification {

  def "fieldset lists returned by a selection are unmodifiable"() {
    given:
    def selection = RepresentationSelection.builder().fields("articles", "title").build()

    when:
    selection.fieldsets().get("articles").add("body")

    then:
    thrown(UnsupportedOperationException)
    selection.fieldsets().get("articles") == ["title"]
  }
}

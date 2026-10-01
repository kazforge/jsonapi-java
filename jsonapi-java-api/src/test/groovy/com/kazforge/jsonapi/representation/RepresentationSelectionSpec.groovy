package com.kazforge.jsonapi.representation

import spock.lang.Specification

class RepresentationSelectionSpec extends Specification {

  def "selection none preserves no inclusion and unrestricted fields"() {
    when:
    def selection = RepresentationSelection.none()

    then:
    selection.includePaths().isEmpty()
    !selection.includeRequested()
    selection.fieldsets().isEmpty()
  }

  def "selection preserves absent versus explicit empty include requests"() {
    given:
    def explicitEmpty = RepresentationSelection.builder().includeRequested().build()
    def explicitPath = RepresentationSelection.builder().include("comments").build()

    expect:
    !RepresentationSelection.none().includeRequested()
    explicitEmpty.includeRequested()
    explicitEmpty.includePaths().isEmpty()
    explicitEmpty != RepresentationSelection.none()
    explicitPath.includeRequested()
  }

  def "selection isolates fieldsets and preserves explicit empty fieldsets"() {
    given:
    def mutableFields = new ArrayList<>(["title", "title", "author"])
    def selection = RepresentationSelection.builder().fields("articles", mutableFields).build()

    when:
    mutableFields.add("body-text")

    then:
    selection.fieldsets() == [articles: ["title", "author"]]
    selection.fieldsets()["articles"] == ["title", "author"]
    RepresentationSelection.builder().fields("articles", []).build().fieldsets() == [articles: []]
  }

  def "selection derivations preserve independent values"() {
    given:
    def path = IncludePath.of("comments.author")

    when:
    def selection = RepresentationSelection.builder()
        .include(path)
        .fields("articles", ["title"])
        .build()

    then:
    selection.includePaths() == [path]
    selection.fieldsets() == [articles: ["title"]]
  }

  def "selection convenience methods merge fields deterministically and compare by value"() {
    given:
    def first = RepresentationSelection.builder()
        .include("comments.author")
        .fields("articles", "title", "comments")
        .fields("articles", "title", "author")
        .build()
    def equal = RepresentationSelection.builder()
        .include(IncludePath.of("comments.author"))
        .fields("articles", ["title", "comments", "author"])
        .build()

    expect:
    first.includePaths() == [
      IncludePath.of("comments.author")
    ]
    first.fieldsets() == [articles: ["title", "comments", "author"]]
    first == equal
    first.hashCode() == equal.hashCode()
    first != RepresentationSelection.none()
    first != (Object) "selection"
  }

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

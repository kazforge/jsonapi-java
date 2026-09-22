package com.kazforge.jsonapi.fixtures.contract

import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleMeta
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorMeta
import com.kazforge.jsonapi.fixtures.domainread.FlatMetaArticle
import spock.lang.Specification

/**
 * Read-Meta characterization contract observed through the Level-1 {@code readOne} entry point.
 * Resource-level {@code meta} and matched relationship-level {@code meta} bind independently under
 * their declared targets, a meta-only relationship binds its meta without binding linkage, absent
 * wire meta leaves the mapped property absent, and a present empty-object meta binds as a present
 * empty value. Concrete adapter subclasses supply the configured runtime.
 */
abstract class ReadMetaCharacterizationSpec extends Specification {

  protected abstract JsonApi api()

  def "binds resource and relationship meta under their declared targets"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"title":"T"},"relationships":' +
        '{"author":{"data":{"type":"people","id":"p1"},"meta":{"displayName":"Alice"}}},' +
        '"meta":{"source":"cms","note":"n"}}}'

    when:
    def bound = api().resources().readOne(json, FlatMetaArticle)

    then:
    bound.meta == new ArticleMeta("cms", "n")
    bound.authorMeta == new AuthorMeta("Alice")
    bound.author == ResourceIdentifier.of("people", "p1")
  }

  def "binds a meta-only relationship's meta without binding linkage"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","relationships":' +
        '{"author":{"meta":{"displayName":"Alice"}}}}}'

    when:
    def bound = api().resources().readOne(json, FlatMetaArticle)

    then:
    bound.meta == null
    bound.authorMeta == new AuthorMeta("Alice")
    bound.author == null
  }

  def "leaves absent resource and relationship meta unbound"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    def bound = api().resources().readOne(json, FlatMetaArticle)

    then:
    bound.meta == null
    bound.authorMeta == null
  }

  def "binds empty-object resource and relationship meta as present empty values"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","relationships":' +
        '{"author":{"data":{"type":"people","id":"p1"},"meta":{}}},"meta":{}}}'

    when:
    def bound = api().resources().readOne(json, FlatMetaArticle)

    then:
    bound.meta == new ArticleMeta(null, null)
    bound.authorMeta == new AuthorMeta(null)
  }
}

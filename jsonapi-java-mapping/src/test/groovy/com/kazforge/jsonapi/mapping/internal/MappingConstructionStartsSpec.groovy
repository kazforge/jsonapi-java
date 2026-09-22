package com.kazforge.jsonapi.mapping.internal

import static com.kazforge.jsonapi.mapping.internal.MappingFakeReadResourceBackend.property
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.ATTRIBUTE
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.ID
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.LOCAL_ID
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.RELATIONSHIP
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.RELATIONSHIP_META
import static com.kazforge.jsonapi.mapping.internal.PropertyRole.RESOURCE_META

import com.kazforge.jsonapi.diagnostic.MappingLocation
import spock.lang.Specification

class MappingConstructionStartsSpec extends Specification {

  def "maps every bindable member's backend external name to its JSON:API start in read order"() {
    given:
    def definition = new ReadResourceDefinition<>(
        'articles',
        property(ID, 'identifier', 'blog_id', 'id'),
        property(LOCAL_ID, 'localKey', 'wire_lid', 'lid'),
        [
          property(ATTRIBUTE, 'title', 'headline', 'headline')
        ],
        [
          property(RELATIONSHIP, 'author', 'writer', 'writer')
        ],
        property(RESOURCE_META, 'meta', 'meta', 'meta'),
        [
          property(RELATIONSHIP_META, 'authorMeta', 'authorMeta', 'writer')
        ])

    when:
    def starts = definition.constructionStarts(
        MappingLocation.of('id'), MappingLocation.of('lid'))

    then:
    starts.keySet() as List == [
      'blog_id',
      'wire_lid',
      'headline',
      'writer',
      'meta',
      'authorMeta'
    ]
    starts.blog_id.location() == MappingLocation.of('id')
    starts.wire_lid.location() == MappingLocation.of('lid')
    starts.headline.location() == MappingLocation.of('attributes', 'headline')
    starts.writer.location() == MappingLocation.of('relationships', 'writer', 'data')
    starts.meta.location() == MappingLocation.of('meta')
    starts.authorMeta.location() == MappingLocation.of('relationships', 'writer', 'meta')
  }

  def "pairs each start with the same opaque property token the definition carries"() {
    given:
    def title = property(ATTRIBUTE, 'title', 'title', 'title')
    def definition = new ReadResourceDefinition<>(
        'articles', null, null, [title], [], null, [])

    when:
    def starts = definition.constructionStarts(null, null)

    then:
    starts.title.property().is(title)
  }

  def "includes identity starts only when the wire member was supplied"() {
    given:
    def definition = new ReadResourceDefinition<>(
        'articles',
        property(ID, 'identifier', 'blog_id', 'id'),
        property(LOCAL_ID, 'localKey', 'wire_lid', 'lid'),
        [],
        [],
        null,
        [])

    when:
    def starts = definition.constructionStarts(null, MappingLocation.of('lid'))

    then:
    starts.keySet() as List == ['wire_lid']
    starts.wire_lid.location() == MappingLocation.of('lid')
  }

  def "omits non-bindable members from construction starts"() {
    given:
    def definition = new ReadResourceDefinition<>(
        'articles',
        property(ID, 'identifier', 'blog_id', 'id'),
        null,
        [
          property(ATTRIBUTE, 'title', 'title', 'title', false)
        ],
        [
          property(RELATIONSHIP, 'author', 'author', 'author', false)
        ],
        property(RESOURCE_META, 'meta', 'meta', 'meta', false),
        [
          property(RELATIONSHIP_META, 'authorMeta', 'authorMeta', 'author', false)
        ])

    when:
    def starts = definition.constructionStarts(
        MappingLocation.of('id'), null)

    then:
    starts.keySet() as List == ['blog_id']
    starts.blog_id.location() == MappingLocation.of('id')
  }

  def "escapes JSON:API member names as pointer segments"() {
    given:
    def definition = new ReadResourceDefinition<>(
        'articles',
        null,
        null,
        [
          property(ATTRIBUTE, 'title', 'a/b~c', 'a/b~c')
        ],
        [
          property(RELATIONSHIP, 'author', 'r/s', 'r/s')
        ],
        null,
        [
          property(RELATIONSHIP_META, 'authorMeta', 'authorMeta', 'r/s')
        ])

    when:
    def starts = definition.constructionStarts(null, null)

    then:
    starts['a/b~c'].location().pointer() == '/attributes/a~1b~0c'
    starts['r/s'].location().pointer() == '/relationships/r~1s/data'
    starts.authorMeta.location().pointer() == '/relationships/r~1s/meta'
  }
}

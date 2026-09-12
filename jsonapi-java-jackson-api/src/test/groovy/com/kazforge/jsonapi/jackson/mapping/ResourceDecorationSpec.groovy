package com.kazforge.jsonapi.jackson.mapping

import com.kazforge.jsonapi.core.model.Link
import com.kazforge.jsonapi.core.model.Links
import spock.lang.Specification

class ResourceDecorationSpec extends Specification {

  def "empty decoration carries no links and no relationships"() {
    expect:
    ResourceDecoration.empty().isEmpty()
    ResourceDecoration.empty().links() == null
    ResourceDecoration.empty().relationships().isEmpty()
  }

  def "builder links sets resource links"() {
    given:
    def links = Links.ofLinks([self: new Link.StringLink("https://example.test/articles/1")])

    when:
    def decoration = ResourceDecoration.builder().links(links).build()

    then:
    decoration.links() == links
    decoration.relationships().isEmpty()
    !decoration.isEmpty()
  }

  def "builder relationship key is logical name"() {
    given:
    def links = Links.ofLinks([self: new Link.StringLink("https://example.test/related")])

    when:
    def decoration = ResourceDecoration.builder()
        .relationship("comments", RelationshipDecoration.of(links))
        .build()

    then:
    decoration.relationships().size() == 1
    decoration.relationships().containsKey("comments")
    decoration.relationships().get("comments").links() == links
  }

  def "relationship decoration of(null) rejects"() {
    when:
    RelationshipDecoration.of(null)

    then:
    thrown(NullPointerException)
  }

  def "resource decoration relationships is defensively copied and unmodifiable"() {
    given:
    def links = Links.ofLinks([self: new Link.StringLink("https://example.test/x")])
    def decoration = ResourceDecoration.builder().links(links)
        .relationship("author", RelationshipDecoration.of(links)).build()
    def mutableCopy = new LinkedHashMap<>(decoration.relationships())
    mutableCopy.put("extra", RelationshipDecoration.empty())

    when:
    decoration.relationships().put("extra", RelationshipDecoration.empty())

    then:
    thrown(UnsupportedOperationException)

    expect:
    decoration.relationships().size() == 1
    mutableCopy.size() == 2
  }

  def "builder duplicate relationship fails"() {
    given:
    def links = Links.ofLinks([self: new Link.StringLink("https://example.test/x")])

    when:
    ResourceDecoration.builder()
        .relationship("author", RelationshipDecoration.of(links))
        .relationship("author", RelationshipDecoration.of(links))
        .build()

    then:
    thrown(IllegalArgumentException)
  }

  def "registry exact match resolution"() {
    given:
    ResourceDecorator<String> decorator = { String s -> ResourceDecoration.empty() }
    def registry = ResourceDecoratorRegistry.builder().register(String, decorator).build()

    expect:
    registry.decoratorFor(String) == decorator
    registry.decoratorFor(Integer) == null
  }

  def "registry duplicate registration fails"() {
    given:
    ResourceDecorator<String> decorator = { String s -> ResourceDecoration.empty() }

    when:
    ResourceDecoratorRegistry.builder().register(String, decorator).register(String, decorator).build()

    then:
    thrown(IllegalArgumentException)
  }

  def "registry is immutable after build"() {
    given:
    def map = [(String): { String s -> ResourceDecoration.empty() } as ResourceDecorator]
    def registry = ResourceDecoratorRegistry.of(map)

    when:
    registry.asMap().put(Integer, { Integer i -> ResourceDecoration.empty() } as ResourceDecorator)

    then:
    thrown(UnsupportedOperationException)
  }

  def "decorator is functional interface"() {
    given:
    ResourceDecorator<String> decorator = { String value ->
      ResourceDecoration.ofLinks(
          Links.ofLinks([self: new Link.StringLink("https://example.test/" + value)]))
    }

    when:
    def decoration = decorator.decorate("a1")

    then:
    decoration.links() != null
    decoration.links().links().containsKey("self")
  }

  def "resource decoration ofLinks and builder relationship with links overload"() {
    given:
    def links = Links.ofLinks([self: new Link.StringLink("https://example.test/x")])
    def links2 = Links.ofLinks([related: new Link.StringLink("https://example.test/y")])

    when:
    def d1 = ResourceDecoration.ofLinks(links)
    def d2 = ResourceDecoration.builder().relationship("author", links2).build()

    then:
    d1.links() == links
    d1.relationships().isEmpty()
    d2.relationships().size() == 1
    d2.relationships().get("author").links() == links2
  }

  def "builder links null rejects"() {
    when:
    ResourceDecoration.builder().links(null)

    then:
    thrown(NullPointerException)
  }

  def "builder relationship null or empty identity rejects"() {
    given:
    def links = Links.ofLinks([self: new Link.StringLink("https://example.test/x")])

    when:
    ResourceDecoration.builder().relationship(null, RelationshipDecoration.of(links))

    then:
    thrown(NullPointerException)

    when:
    ResourceDecoration.builder().relationship("", RelationshipDecoration.of(links))

    then:
    thrown(IllegalArgumentException)

    when:
    ResourceDecoration.builder().relationship("author", (RelationshipDecoration) null)

    then:
    thrown(NullPointerException)

    when:
    ResourceDecoration.builder().relationship("author", (Links) null)

    then:
    thrown(NullPointerException)
  }

  def "resource decoration isEmpty variations"() {
    given:
    def links = Links.ofLinks([self: new Link.StringLink("https://example.test/x")])
    def emptyLinks = Links.empty()

    expect:
    ResourceDecoration.empty().isEmpty()
    !ResourceDecoration.ofLinks(emptyLinks).isEmpty()
    !ResourceDecoration.ofLinks(links).isEmpty()
    ResourceDecoration.builder().relationship("a", RelationshipDecoration.empty()).build().isEmpty()
    !ResourceDecoration.builder().relationship("a", RelationshipDecoration.of(links)).build().isEmpty()
    !ResourceDecoration.builder().relationship("a", new RelationshipDecoration(emptyLinks)).build().isEmpty()
  }

  def "relationship decoration factories and isEmpty"() {
    given:
    def links = Links.ofLinks([self: new Link.StringLink("https://example.test/x")])

    expect:
    RelationshipDecoration.empty().isEmpty()
    RelationshipDecoration.empty().links() == null
    RelationshipDecoration.of(links).links() == links
    !RelationshipDecoration.of(links).isEmpty()
    RelationshipDecoration.links(links).links() == links
    !RelationshipDecoration.of(links).isEmpty()
    RelationshipDecoration.empty().isEmpty()
    !new RelationshipDecoration(Links.empty()).isEmpty()
  }

  def "resource decoration equals hashCode toString"() {
    given:
    def links = Links.ofLinks([self: new Link.StringLink("https://example.test/x")])
    def d1 = ResourceDecoration.builder().links(links).relationship("a", RelationshipDecoration.of(links)).build()
    def d2 = ResourceDecoration.builder().links(links).relationship("a", RelationshipDecoration.of(links)).build()
    def d3 = ResourceDecoration.empty()

    expect:
    d1 == d2
    d1.hashCode() == d2.hashCode()
    d1.toString().contains("ResourceDecoration")
    d1 != d3
  }

  def "relationship decoration equals hashcode"() {
    given:
    def links = Links.ofLinks([self: new Link.StringLink("https://example.test/x")])
    def r1 = RelationshipDecoration.of(links)
    def r2 = RelationshipDecoration.of(links)
    def r3 = RelationshipDecoration.empty()

    expect:
    r1 == r2
    r1.hashCode() == r2.hashCode()
    r1 != r3
  }

  def "registry size and isEmpty and of"() {
    expect:
    ResourceDecoratorRegistry.empty().isEmpty()
    ResourceDecoratorRegistry.empty().size() == 0
    ResourceDecoratorRegistry.of([:]).isEmpty()
    ResourceDecoratorRegistry.of([:]) == ResourceDecoratorRegistry.empty()

    when:
    def reg = ResourceDecoratorRegistry.builder()
        .register(String, { s -> ResourceDecoration.empty() } as ResourceDecorator)
        .register(Integer, { i -> ResourceDecoration.empty() } as ResourceDecorator)
        .build()

    then:
    reg.size() == 2
    !reg.isEmpty()
    reg.decoratorFor(String) != null
    reg.asMap().size() == 2
  }

  def "registry decoratorFor null rejects"() {
    when:
    ResourceDecoratorRegistry.empty().decoratorFor(null)

    then:
    thrown(NullPointerException)
  }

  def "registry of null rejects"() {
    when:
    ResourceDecoratorRegistry.of(null)

    then:
    thrown(NullPointerException)
  }

  def "registry builder register null rejects"() {
    when:
    ResourceDecoratorRegistry.builder().register(null, { s -> ResourceDecoration.empty() } as ResourceDecorator)

    then:
    thrown(NullPointerException)

    when:
    ResourceDecoratorRegistry.builder().register(String, null)

    then:
    thrown(NullPointerException)
  }
}

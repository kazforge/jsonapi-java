package com.kazforge.jsonapi.mapping

import com.kazforge.jsonapi.api.JsonApiPatches
import com.kazforge.jsonapi.api.JsonApiResources
import com.kazforge.jsonapi.diagnostic.MappingLocation
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import spock.lang.Specification

import static com.kazforge.jsonapi.mapping.BindingSource.Presence.*

class BindingSourceSpec extends Specification {

  def "only present states carry locations"() {
    given:
    def location = present ? MappingLocation.of("data", "attributes", "title") : null

    expect:
    new BindingSource(state, location) == new BindingSource(state, location)
    new BindingSource(state, location).presence() == state

    when:
    new BindingSource(state, present ? null : MappingLocation.of("data"))

    then:
    thrown(IllegalArgumentException)

    where:
    state         | present
    OMITTED       | false
    PRESENT_NULL  | true
    PRESENT_VALUE | true
    UNRESOLVED    | false
  }

  def "presence is required"() {
    when:
    new BindingSource(null, null)

    then:
    thrown(NullPointerException)
  }

  def "new facet operations are default unsupported rather than fabricated attribution"() {
    given:
    def implementation = Proxy.newProxyInstance(
        facet.classLoader, [facet] as Class[],
        { proxy, method, args -> InvocationHandler.invokeDefault(proxy, method, args) } as InvocationHandler)
    def methods = facet.declaredMethods.findAll { it.name.endsWith("WithSources") }

    expect:
    !methods.empty
    methods.every { method ->
      assert method.isDefault()
      try {
        method.invoke(implementation, new Object[method.parameterCount])
        assert false: "Expected explicit unsupported behavior"
      } catch (InvocationTargetException failure) {
        assert failure.cause instanceof UnsupportedOperationException
      }
      true
    }

    where:
    facet << [
      JsonApiResources,
      JsonApiPatches
    ]
  }
}

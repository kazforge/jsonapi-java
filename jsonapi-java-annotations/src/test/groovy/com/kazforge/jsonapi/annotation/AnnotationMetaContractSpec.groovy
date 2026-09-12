package com.kazforge.jsonapi.annotation

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.lang.reflect.Method
import spock.lang.Specification

class AnnotationMetaContractSpec extends Specification {

  def "JsonApiResource has runtime retention, Documented, TYPE target, required type element, and is not Inherited"() {
    expect:
    assertRuntimeDocumented(JsonApiResource)
    JsonApiResource.getAnnotation(Target).value() as Set == ([ElementType.TYPE] as Set)
    JsonApiResource.getAnnotation(Inherited) == null

    and:
    def typeMethod = JsonApiResource.getDeclaredMethod("type")
    typeMethod.returnType == String
    typeMethod.defaultValue == null
    JsonApiResource.declaredMethods*.name as Set == (["type"] as Set)
  }

  def "JsonApiId is a runtime Documented marker on property targets and is not Inherited"() {
    expect:
    assertRuntimeDocumented(JsonApiId)
    assertPropertyTargets(JsonApiId)
    JsonApiId.getAnnotation(Inherited) == null
    JsonApiId.declaredMethods.length == 0
  }

  def "JsonApiLocalId is a runtime Documented marker on property targets and is not Inherited"() {
    expect:
    assertRuntimeDocumented(JsonApiLocalId)
    assertPropertyTargets(JsonApiLocalId)
    JsonApiLocalId.getAnnotation(Inherited) == null
    JsonApiLocalId.declaredMethods.length == 0
  }

  def "JsonApiAttribute is a runtime Documented marker on property targets and is not Inherited"() {
    expect:
    assertRuntimeDocumented(JsonApiAttribute)
    assertPropertyTargets(JsonApiAttribute)
    JsonApiAttribute.getAnnotation(Inherited) == null
    JsonApiAttribute.declaredMethods.length == 0
  }

  def "JsonApiRelationship is a runtime Documented marker on property targets and is not Inherited"() {
    expect:
    assertRuntimeDocumented(JsonApiRelationship)
    assertPropertyTargets(JsonApiRelationship)
    JsonApiRelationship.getAnnotation(Inherited) == null
    JsonApiRelationship.declaredMethods.length == 0
  }

  def "JsonApiMeta is a runtime Documented marker on property targets and is not Inherited"() {
    expect:
    assertRuntimeDocumented(JsonApiMeta)
    assertPropertyTargets(JsonApiMeta)
    JsonApiMeta.getAnnotation(Inherited) == null
    JsonApiMeta.declaredMethods.length == 0
  }

  def "JsonApiRelationshipMeta has a required non-defaulted relationship element on property targets and is not Inherited"() {
    expect:
    assertRuntimeDocumented(JsonApiRelationshipMeta)
    assertPropertyTargets(JsonApiRelationshipMeta)
    JsonApiRelationshipMeta.getAnnotation(Inherited) == null

    and:
    Method relationshipMethod = JsonApiRelationshipMeta.getDeclaredMethod("relationship")
    relationshipMethod.returnType == String
    relationshipMethod.defaultValue == null
    JsonApiRelationshipMeta.declaredMethods*.name as Set == (["relationship"] as Set)
  }

  private static void assertRuntimeDocumented(Class<?> annotationType) {
    assert annotationType.getAnnotation(Retention).value() == RetentionPolicy.RUNTIME
    assert annotationType.getAnnotation(Documented) != null
  }

  private static void assertPropertyTargets(Class<?> annotationType) {
    assert annotationType.getAnnotation(Target).value() as Set == ([
      ElementType.FIELD,
      ElementType.METHOD,
      ElementType.PARAMETER,
      ElementType.RECORD_COMPONENT
    ] as Set)
  }
}

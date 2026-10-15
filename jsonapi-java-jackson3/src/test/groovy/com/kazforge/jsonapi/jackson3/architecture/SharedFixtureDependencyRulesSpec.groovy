package com.kazforge.jsonapi.jackson3.architecture

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import spock.lang.Shared
import spock.lang.Specification

class SharedFixtureDependencyRulesSpec extends Specification {

  private static final String[] APPLICATION_PACKAGES = [
    "java..",
    "org.jspecify.annotations..",
    "com.kazforge.jsonapi.annotation..",
    "com.kazforge.jsonapi.core.model..",
    "com.kazforge.jsonapi",
    "com.kazforge.jsonapi.api..",
    "com.kazforge.jsonapi.document..",
    "com.kazforge.jsonapi.mapping..",
    "com.kazforge.jsonapi.patch..",
    "com.kazforge.jsonapi.representation..",
    "com.kazforge.jsonapi.diagnostic..",
    "com.kazforge.jsonapi.fixtures..",
    "com.fasterxml.jackson.annotation.."
  ]

  @Shared
  JavaClasses sharedFixtureClasses = new ClassFileImporter()
  .importPackages("com.kazforge.jsonapi.fixtures..")

  def "shared fixtures do not depend on mapping implementation details"() {
    expect:
    noClasses()
        .that().resideInAPackage("com.kazforge.jsonapi.fixtures..")
        .should().dependOnClassesThat()
        .resideInAPackage("com.kazforge.jsonapi.jackson3.internal.mapping..")
        .allowEmptyShould(false)
        .check(sharedFixtureClasses)
  }

  def "shared passive fixtures depend only on allowed application-shaped packages"() {
    expect:
    classes()
        .that().resideInAPackage("com.kazforge.jsonapi.fixtures..")
        .should().onlyDependOnClassesThat()
        .resideInAnyPackage(APPLICATION_PACKAGES)
        .allowEmptyShould(false)
        .check(sharedFixtureClasses)
  }
}

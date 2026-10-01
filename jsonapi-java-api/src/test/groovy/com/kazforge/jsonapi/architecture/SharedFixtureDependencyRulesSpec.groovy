package com.kazforge.jsonapi.architecture

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
        .resideInAPackage("com.kazforge.jsonapi.mapping.internal..")
        .allowEmptyShould(false)
        .check(sharedFixtureClasses)
  }

  def "shared passive fixtures outside the contract package depend only on allowed packages"() {
    expect:
    classes()
        .that().resideInAPackage("com.kazforge.jsonapi.fixtures..")
        .and().resideOutsideOfPackage("com.kazforge.jsonapi.fixtures.contract..")
        .should().onlyDependOnClassesThat()
        .resideInAnyPackage(APPLICATION_PACKAGES)
        .allowEmptyShould(false)
        .check(sharedFixtureClasses)
  }

  def "shared contract-fixture carriers depend only on allowed application-shaped packages"() {
    expect:
    classes()
        .that().resideInAPackage("com.kazforge.jsonapi.fixtures.contract..")
        .and().haveSimpleNameNotEndingWith("CharacterizationSpec")
        .should().onlyDependOnClassesThat()
        .resideInAnyPackage(APPLICATION_PACKAGES)
        .allowEmptyShould(false)
        .check(sharedFixtureClasses)
  }

  def "shared characterization contract specs depend only on allowed contract packages"() {
    expect:
    classes()
        .that().resideInAPackage("com.kazforge.jsonapi.fixtures.contract..")
        .and().haveSimpleNameEndingWith("CharacterizationSpec")
        .should().onlyDependOnClassesThat()
        .resideInAnyPackage(([
          "groovy..",
          "org.codehaus.groovy..",
          "spock..",
          "org.spockframework..",
        ] + APPLICATION_PACKAGES.toList()) as String[])
        .allowEmptyShould(false)
        .check(sharedFixtureClasses)
  }
}

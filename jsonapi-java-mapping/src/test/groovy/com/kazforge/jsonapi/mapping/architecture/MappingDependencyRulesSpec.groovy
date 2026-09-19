package com.kazforge.jsonapi.mapping.architecture

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import spock.lang.Shared
import spock.lang.Specification

class MappingDependencyRulesSpec extends Specification {

  @Shared
  JavaClasses mappingClasses = new ClassFileImporter()
  .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
  .importPackages("com.kazforge.jsonapi.mapping..")

  def "mapping domain does not depend on concrete mapper implementations"() {
    expect:
    noClasses()
        .that()
        .resideInAPackage("com.kazforge.jsonapi.mapping..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
        "com.fasterxml.jackson..",
        "tools.jackson..",
        "com.kazforge.jsonapi.jackson.internal..",
        "com.kazforge.jsonapi.jackson2..",
        "com.kazforge.jsonapi.jackson3..")
        .check(mappingClasses)
  }
}

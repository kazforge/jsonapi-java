package com.kazforge.jsonapi.core.architecture

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import spock.lang.Shared
import spock.lang.Specification

class CoreDependencyRulesSpec extends Specification {

  @Shared
  JavaClasses coreClasses = new ClassFileImporter()
  .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
  .importPackages("com.kazforge.jsonapi.core..")

  def "core responsibility selectors are non-empty"() {
    expect:
    !coreClasses.findAll { candidate ->
      candidate.packageName == "com.kazforge.jsonapi.core.aggregate" ||
          candidate.packageName.startsWith("com.kazforge.jsonapi.core.aggregate.")
    }.isEmpty()
    !coreClasses.findAll { candidate ->
      candidate.packageName == "com.kazforge.jsonapi.core.model" ||
          candidate.packageName.startsWith("com.kazforge.jsonapi.core.model.")
    }.isEmpty()
    !coreClasses.findAll { candidate ->
      candidate.packageName == "com.kazforge.jsonapi.core.internal" ||
          candidate.packageName.startsWith("com.kazforge.jsonapi.core.internal.")
    }.isEmpty()
    !coreClasses.findAll { candidate ->
      candidate.packageName == "com.kazforge.jsonapi.core.validation" ||
          candidate.packageName.startsWith("com.kazforge.jsonapi.core.validation.")
    }.isEmpty()
  }

  def "validation does not depend on higher core responsibilities"() {
    expect:
    noClasses()
        .that()
        .resideInAPackage("com.kazforge.jsonapi.core.validation..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
        "com.kazforge.jsonapi.core.internal..",
        "com.kazforge.jsonapi.core.model..",
        "com.kazforge.jsonapi.core.aggregate..")
        .check(coreClasses)
  }

  def "internal does not depend on model or aggregate"() {
    expect:
    noClasses()
        .that()
        .resideInAPackage("com.kazforge.jsonapi.core.internal..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
        "com.kazforge.jsonapi.core.model..",
        "com.kazforge.jsonapi.core.aggregate..")
        .check(coreClasses)
  }

  def "model does not depend on aggregate"() {
    expect:
    noClasses()
        .that()
        .resideInAPackage("com.kazforge.jsonapi.core.model..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.kazforge.jsonapi.core.aggregate..")
        .check(coreClasses)
  }
}

package io.github.kazemek.jsonapi.jackson2.architecture

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption

import spock.lang.Shared
import spock.lang.Specification

class Jackson2DependencyRulesSpec extends Specification {

  @Shared
  JavaClasses jackson2Classes = new ClassFileImporter()
  .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
  .importPackages("io.github.kazemek.jsonapi.jackson2")

  @Shared
  JavaClasses commonClasses = new ClassFileImporter()
  .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
  .importPackages("io.github.kazemek.jsonapi.jackson..")

  @Shared
  JavaClasses sharedFixtureClasses = new ClassFileImporter()
  .importPackages("io.github.kazemek.jsonapi.fixtures..")

  def "jackson2 production types depend only on allowed packages"() {
    expect:
    classes()
        .that()
        .resideInAPackage("io.github.kazemek.jsonapi.jackson2..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
        "java..",
        "org.jspecify.annotations..",
        "io.github.kazemek.jsonapi.core.model..",
        "io.github.kazemek.jsonapi.core.validation..",
        "io.github.kazemek.jsonapi.annotation..",
        "io.github.kazemek.jsonapi.jackson..",
        "io.github.kazemek.jsonapi.jackson2..",
        "com.fasterxml.jackson..")
        .check(jackson2Classes)
  }

  def "jackson2 exposes no duplicate public common contract types"() {
    given:
    def commonContractNames = commonClasses.findAll { JavaClass candidate ->
      (candidate.packageName == "io.github.kazemek.jsonapi.jackson" ||
          candidate.packageName.startsWith("io.github.kazemek.jsonapi.jackson.")) &&
          candidate.modifiers.contains(JavaModifier.PUBLIC) && candidate.topLevelClass
    }.collect { JavaClass candidate -> candidate.simpleName }.toSet()
    def jackson2TypeNames = jackson2Classes.findAll { JavaClass candidate ->
      candidate.packageName.startsWith("io.github.kazemek.jsonapi.jackson2") &&
          candidate.modifiers.contains(JavaModifier.PUBLIC) && candidate.topLevelClass
    }.collect { JavaClass candidate -> candidate.simpleName }.toSet()

    expect:
    commonContractNames.intersect(jackson2TypeNames).isEmpty()
  }

  def "shared test fixtures depend only on allowed application-shaped packages"() {
    expect:
    classes()
        .that()
        .resideInAPackage("io.github.kazemek.jsonapi.fixtures..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
        "java..",
        "org.jspecify.annotations..",
        "io.github.kazemek.jsonapi.annotation..",
        "io.github.kazemek.jsonapi.core.model..",
        "io.github.kazemek.jsonapi.jackson..",
        "io.github.kazemek.jsonapi.fixtures..",
        "com.fasterxml.jackson.annotation..")
        .check(sharedFixtureClasses)
  }
}

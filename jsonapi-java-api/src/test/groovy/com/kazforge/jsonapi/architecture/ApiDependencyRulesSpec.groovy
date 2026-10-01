package com.kazforge.jsonapi.architecture

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import spock.lang.Shared
import spock.lang.Specification

class ApiDependencyRulesSpec extends Specification {

  private static final String[] CONTRACT_PACKAGES = [
    "com.kazforge.jsonapi",
    "com.kazforge.jsonapi.api..",
    "com.kazforge.jsonapi.document..",
    "com.kazforge.jsonapi.mapping..",
    "com.kazforge.jsonapi.patch..",
    "com.kazforge.jsonapi.representation..",
    "com.kazforge.jsonapi.diagnostic.."
  ]

  @Shared
  JavaClasses commonClasses = new ClassFileImporter()
  .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
  .importPackages(CONTRACT_PACKAGES)

  def "neutral contract responsibility #packagePattern is non-empty"(String packagePattern) {
    expect:
    commonClasses.any { candidate ->
      resideInAPackage(packagePattern).test(candidate) &&
          !resideInAPackage("com.kazforge.jsonapi.mapping.internal..").test(candidate)
    }

    where:
    packagePattern << CONTRACT_PACKAGES
  }

  def "neutral contract production types depend only on allowed packages"() {
    expect:
    classes()
        .that().resideInAnyPackage(CONTRACT_PACKAGES)
        .and().resideOutsideOfPackage("com.kazforge.jsonapi.mapping.internal..")
        .should().onlyDependOnClassesThat()
        .resideInAnyPackage(([
          "java..",
          "org.jspecify.annotations..",
          "com.kazforge.jsonapi.core.aggregate..",
          "com.kazforge.jsonapi.core.model..",
          "com.kazforge.jsonapi.core.validation..",
        ] + CONTRACT_PACKAGES.toList()) as String[])
        .check(commonClasses)
  }

  def "neutral production types do not depend on mapping implementation detail"() {
    expect:
    // The mapping contract prefix also contains implementation; the allowlist alone is insufficient.
    noClasses()
        .that().resideInAnyPackage(CONTRACT_PACKAGES)
        .and().resideOutsideOfPackage("com.kazforge.jsonapi.mapping.internal..")
        .should().dependOnClassesThat()
        .resideInAPackage("com.kazforge.jsonapi.mapping.internal..")
        .check(commonClasses)
  }
}

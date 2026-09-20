package com.kazforge.jsonapi.mapping.architecture

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import spock.lang.Shared
import spock.lang.Specification

class MappingDependencyRulesSpec extends Specification {

  @Shared
  def mappingClasses = new ClassFileImporter()
  .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
  .importPackages("com.kazforge.jsonapi.mapping.internal..")

  def "mapping production selector is non-empty"() {
    expect:
    !mappingClasses.findAll { candidate ->
      candidate.packageName == "com.kazforge.jsonapi.mapping.internal" ||
          candidate.packageName.startsWith("com.kazforge.jsonapi.mapping.internal.")
    }.isEmpty()
  }

  def "mapping production types depend only on allowed packages"() {
    expect:
    classes()
        .that()
        .resideInAPackage("com.kazforge.jsonapi.mapping.internal..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
        "java..",
        "org.jspecify.annotations..",
        "com.kazforge.jsonapi.core.aggregate..",
        "com.kazforge.jsonapi.core.model..",
        "com.kazforge.jsonapi.core.validation..",
        "com.kazforge.jsonapi",
        "com.kazforge.jsonapi.api..",
        "com.kazforge.jsonapi.document..",
        "com.kazforge.jsonapi.mapping..",
        "com.kazforge.jsonapi.patch..",
        "com.kazforge.jsonapi.representation..",
        "com.kazforge.jsonapi.diagnostic..",
        "com.kazforge.jsonapi.internal..",
        "com.kazforge.jsonapi.mapping.internal..")
        .check(mappingClasses)
  }
}

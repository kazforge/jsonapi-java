package io.github.kazemek.jsonapi.query

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import io.github.kazemek.jsonapi.core.validation.MemberNames
import spock.lang.Shared
import spock.lang.Specification

class QueryArchitectureSpec extends Specification {

  @Shared
  def queryClasses = new ClassFileImporter()
  .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
  .importPackages("io.github.kazemek.jsonapi.query..")

  def "query production types stay within the neutral query allow-list"() {
    expect:
    classes()
        .that()
        .resideInAPackage("io.github.kazemek.jsonapi.query..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
        "java..",
        "org.jspecify.annotations..",
        "io.github.kazemek.jsonapi.query..",
        "io.github.kazemek.jsonapi.core.validation..",
        "io.github.kazemek.jsonapi.jackson.representation..")
        .check(queryClasses)
  }

  def "query production types use only the permitted core validation type"() {
    expect:
    queryClasses.collectMany { candidate ->
      candidate.directDependenciesFromSelf.findAll { dependency ->
        dependency.targetClass.packageName.startsWith("io.github.kazemek.jsonapi.core.validation") &&
            dependency.targetClass.fullName != MemberNames.name
      }
    }.isEmpty()
  }
}

package io.github.kazemek.jsonapi.jackson.architecture

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes

import io.github.kazemek.jsonapi.jackson.ArchitectureConstructorSignatureLeakFixture
import io.github.kazemek.jsonapi.jackson.ArchitectureSignatureLeakFixture
import io.github.kazemek.jsonapi.jackson.internal.ArchitectureInternalException
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import java.util.LinkedHashSet
import spock.lang.Shared
import spock.lang.Specification

class JacksonApiDependencyRulesSpec extends Specification {

  @Shared
  JavaClasses commonClasses = new ClassFileImporter()
  .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
  .importPackages("io.github.kazemek.jsonapi.jackson..")

  def "common contract production types depend only on allowed packages"() {
    expect:
    classes()
        .that()
        .resideInAPackage("io.github.kazemek.jsonapi.jackson..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
        "java..",
        "org.jspecify.annotations..",
        "io.github.kazemek.jsonapi.core.model..",
        "io.github.kazemek.jsonapi.core.validation..",
        "io.github.kazemek.jsonapi.jackson..")
        .check(commonClasses)
  }

  def "supported public signatures do not expose shared internal implementation types"() {
    given:
    def violations = commonClasses.findAll { JavaClass candidate ->
      isSupportedPublicType(candidate)
    }.collectMany { JavaClass candidate ->
      exposedTypes(candidate)
          .findAll { JavaClass dependency -> isInternalType(dependency) }
          .collect { JavaClass dependency -> "${candidate.fullName} -> ${dependency.fullName}" }
    }

    expect:
    assert violations.isEmpty(), violations.join(System.lineSeparator())
  }

  def "supported public signatures detect declared shared internal exceptions"() {
    given:
    def fixtureClasses = new ClassFileImporter()
        .importClasses(
        ArchitectureConstructorSignatureLeakFixture,
        ArchitectureSignatureLeakFixture,
        ArchitectureInternalException)
    def violations = fixtureClasses.findAll { JavaClass candidate ->
      isSupportedPublicType(candidate)
    }.collectMany { JavaClass candidate ->
      exposedTypes(candidate)
          .findAll { JavaClass dependency -> isInternalType(dependency) }
          .collect { JavaClass dependency -> "${candidate.fullName} -> ${dependency.fullName}" }
    }

    expect:
    violations*.toString().toSet() == [
      "io.github.kazemek.jsonapi.jackson.ArchitectureConstructorSignatureLeakFixture -> " +
      "io.github.kazemek.jsonapi.jackson.internal.ArchitectureInternalException",
      "io.github.kazemek.jsonapi.jackson.ArchitectureSignatureLeakFixture -> " +
      "io.github.kazemek.jsonapi.jackson.internal.ArchitectureInternalException"
    ].toSet()
  }

  def "level-1 application contract stays free of Jackson implementation types"() {
    expect:
    classes()
        .that()
        .resideInAPackage("io.github.kazemek.jsonapi.jackson.api..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
        "java..",
        "org.jspecify.annotations..",
        "io.github.kazemek.jsonapi.core.model..",
        "io.github.kazemek.jsonapi.core.validation..",
        "io.github.kazemek.jsonapi.jackson..")
        .check(commonClasses)
  }

  def "level-1 application contract exposes no Jackson implementation dependency"() {
    expect:
    classes()
        .that()
        .resideInAPackage("io.github.kazemek.jsonapi.jackson.api..")
        .should()
        .onlyDependOnClassesThat()
        .resideOutsideOfPackages("tools.jackson..", "com.fasterxml..")
        .check(commonClasses)
  }

  private static boolean isSupportedPublicType(JavaClass candidate) {
    candidate.modifiers.contains(JavaModifier.PUBLIC) &&
        isSupportedPackage(candidate.packageName)
  }

  private static boolean isSupportedPackage(String packageName) {
    packageName == "io.github.kazemek.jsonapi.jackson" ||
        (packageName.startsWith("io.github.kazemek.jsonapi.jackson.") &&
        !isInternalPackage(packageName))
  }

  private static boolean isInternalPackage(String packageName) {
    packageName == "io.github.kazemek.jsonapi.jackson.internal" ||
        packageName.startsWith("io.github.kazemek.jsonapi.jackson.internal.")
  }

  private static boolean isInternalType(JavaClass candidate) {
    isInternalPackage(candidate.packageName)
  }

  private static Set<JavaClass> exposedTypes(JavaClass candidate) {
    def types = new LinkedHashSet<JavaClass>()
    candidate.interfaces.each { type -> types.addAll(type.allInvolvedRawTypes) }
    candidate.superclass.ifPresent { type -> types.addAll(type.allInvolvedRawTypes) }
    candidate.typeParameters.each { type -> types.addAll(type.allInvolvedRawTypes) }
    candidate.constructors.findAll { isExposedMember(it) }.each { member ->
      types.addAll(member.allInvolvedRawTypes)
      types.addAll(member.exceptionTypes)
    }
    candidate.methods.findAll { isExposedMember(it) }.each { member ->
      types.addAll(member.allInvolvedRawTypes)
      types.addAll(member.exceptionTypes)
    }
    candidate.fields.findAll { isExposedMember(it) }.each { member ->
      types.addAll(member.allInvolvedRawTypes)
    }
    types
  }

  private static boolean isExposedMember(member) {
    member.modifiers.contains(JavaModifier.PUBLIC) ||
        member.modifiers.contains(JavaModifier.PROTECTED)
  }
}

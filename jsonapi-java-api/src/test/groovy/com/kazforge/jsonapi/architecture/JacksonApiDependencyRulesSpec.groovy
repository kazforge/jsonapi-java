package com.kazforge.jsonapi.architecture

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes

import com.kazforge.jsonapi.ArchitectureConstructorSignatureLeakFixture
import com.kazforge.jsonapi.ArchitectureSignatureLeakFixture
import com.kazforge.jsonapi.internal.ArchitectureInternalException
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
  .importPackages(
  "com.kazforge.jsonapi",
  "com.kazforge.jsonapi.api..",
  "com.kazforge.jsonapi.document..",
  "com.kazforge.jsonapi.mapping..",
  "com.kazforge.jsonapi.patch..",
  "com.kazforge.jsonapi.representation..",
  "com.kazforge.jsonapi.diagnostic..",
  "com.kazforge.jsonapi.internal..")

  def "common contract production types depend only on allowed packages"() {
    expect:
    classes()
        .that()
        .resideInAnyPackage(
        "com.kazforge.jsonapi",
        "com.kazforge.jsonapi.api..",
        "com.kazforge.jsonapi.document..",
        "com.kazforge.jsonapi.mapping..",
        "com.kazforge.jsonapi.patch..",
        "com.kazforge.jsonapi.representation..",
        "com.kazforge.jsonapi.diagnostic..",
        "com.kazforge.jsonapi.internal..")
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
        "com.kazforge.jsonapi.internal..")
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
      "com.kazforge.jsonapi.ArchitectureConstructorSignatureLeakFixture -> " +
      "com.kazforge.jsonapi.internal.ArchitectureInternalException",
      "com.kazforge.jsonapi.ArchitectureSignatureLeakFixture -> " +
      "com.kazforge.jsonapi.internal.ArchitectureInternalException"
    ].toSet()
  }

  def "supported public type selector matches known neutral contract types"() {
    expect:
    [
      "com.kazforge.jsonapi.api.JsonApi",
      "com.kazforge.jsonapi.document.DocumentReadContext",
      "com.kazforge.jsonapi.mapping.MappedDocument",
      "com.kazforge.jsonapi.patch.PatchPresence",
      "com.kazforge.jsonapi.representation.RepresentationSelection",
      "com.kazforge.jsonapi.diagnostic.JsonApiMappingException"
    ].every { String typeName ->
      def candidate = commonClasses.find { JavaClass it -> it.fullName == typeName }
      candidate != null && isSupportedPublicType(candidate)
    }
  }

  def "level-1 application contract stays free of Jackson implementation types"() {
    expect:
    classes()
        .that()
        .resideInAPackage("com.kazforge.jsonapi.api..")
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
        "com.kazforge.jsonapi.internal..")
        .check(commonClasses)
  }

  def "level-1 application contract exposes no Jackson implementation dependency"() {
    expect:
    classes()
        .that()
        .resideInAPackage("com.kazforge.jsonapi.api..")
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
    packageName == "com.kazforge.jsonapi" ||
        (isNeutralContractPackage(packageName) && !isInternalPackage(packageName))
  }

  private static boolean isNeutralContractPackage(String packageName) {
    isExactOrDescendant(packageName, "com.kazforge.jsonapi.api") ||
        isExactOrDescendant(packageName, "com.kazforge.jsonapi.document") ||
        isExactOrDescendant(packageName, "com.kazforge.jsonapi.mapping") ||
        isExactOrDescendant(packageName, "com.kazforge.jsonapi.patch") ||
        isExactOrDescendant(packageName, "com.kazforge.jsonapi.representation") ||
        isExactOrDescendant(packageName, "com.kazforge.jsonapi.diagnostic")
  }

  private static boolean isExactOrDescendant(String packageName, String basePackage) {
    packageName == basePackage || packageName.startsWith(basePackage + ".")
  }

  private static boolean isInternalPackage(String packageName) {
    packageName == "com.kazforge.jsonapi.internal" ||
        packageName.startsWith("com.kazforge.jsonapi.internal.")
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

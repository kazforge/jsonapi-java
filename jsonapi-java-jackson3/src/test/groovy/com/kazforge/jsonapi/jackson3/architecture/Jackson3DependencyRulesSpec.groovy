package com.kazforge.jsonapi.jackson3.architecture

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import spock.lang.Shared
import spock.lang.Specification

class Jackson3DependencyRulesSpec extends Specification {

  @Shared
  JavaClasses jackson3Classes = new ClassFileImporter()
  .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
  .importPackages("com.kazforge.jsonapi.jackson3..")

  @Shared
  JavaClasses commonClasses = new ClassFileImporter()
  .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
  .importPackages("com.kazforge.jsonapi.jackson..")

  @Shared
  JavaClasses sharedFixtureClasses = new ClassFileImporter()
  .importPackages("com.kazforge.jsonapi.fixtures..")

  def "jackson3 production types depend only on allowed packages"() {
    expect:
    classes()
        .that()
        .resideInAPackage("com.kazforge.jsonapi.jackson3..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
        "java..",
        "org.jspecify.annotations..",
        "com.kazforge.jsonapi.core.aggregate..",
        "com.kazforge.jsonapi.core.model..",
        "com.kazforge.jsonapi.core.validation..",
        "com.kazforge.jsonapi.annotation..",
        "com.kazforge.jsonapi.jackson..",
        "com.kazforge.jsonapi.jackson3..",
        "tools.jackson..")
        .check(jackson3Classes)
  }

  def "jackson3 responsibility selectors are non-empty and disjoint"() {
    given:
    def root = jackson3Classes.findAll { JavaClass candidate ->
      candidate.packageName == "com.kazforge.jsonapi.jackson3"
    }
    def mapping = jackson3Classes.findAll { JavaClass candidate ->
      candidate.packageName == "com.kazforge.jsonapi.jackson3.mapping" ||
          candidate.packageName.startsWith("com.kazforge.jsonapi.jackson3.mapping.")
    }
    def internal = jackson3Classes.findAll { JavaClass candidate ->
      candidate.packageName == "com.kazforge.jsonapi.jackson3.internal"
    }
    def codec = jackson3Classes.findAll { JavaClass candidate ->
      candidate.packageName == "com.kazforge.jsonapi.jackson3.internal.codec" ||
          candidate.packageName.startsWith("com.kazforge.jsonapi.jackson3.internal.codec.")
    }

    expect:
    !root.isEmpty()
    !mapping.isEmpty()
    !internal.isEmpty()
    !codec.isEmpty()

    and:
    root.intersect(mapping).isEmpty()
    root.intersect(internal).isEmpty()
    root.intersect(codec).isEmpty()
    mapping.intersect(internal).isEmpty()
    mapping.intersect(codec).isEmpty()
    internal.intersect(codec).isEmpty()
  }

  def "jackson3 mapping contracts do not depend on composition or internals"() {
    expect:
    noClasses()
        .that()
        .resideInAPackage("com.kazforge.jsonapi.jackson3.mapping..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
        "com.kazforge.jsonapi.jackson3",
        "com.kazforge.jsonapi.jackson3.internal",
        "com.kazforge.jsonapi.jackson3.internal.codec..")
        .check(jackson3Classes)
  }

  def "jackson3 codec does not depend on composition, mapping, or exact internal"() {
    expect:
    noClasses()
        .that()
        .resideInAPackage("com.kazforge.jsonapi.jackson3.internal.codec..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
        "com.kazforge.jsonapi.jackson3",
        "com.kazforge.jsonapi.jackson3.mapping..",
        "com.kazforge.jsonapi.jackson3.internal")
        .check(jackson3Classes)
  }

  def "jackson3 exact internal does not depend on composition or codec"() {
    expect:
    noClasses()
        .that()
        .resideInAPackage("com.kazforge.jsonapi.jackson3.internal")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
        "com.kazforge.jsonapi.jackson3",
        "com.kazforge.jsonapi.jackson3.internal.codec..")
        .check(jackson3Classes)
  }

  def "jackson3 exposes no duplicate public common contract types"() {
    given:
    def commonContractNames = commonClasses.findAll { JavaClass candidate ->
      isSupportedCommonType(candidate)
    }.collect { JavaClass candidate -> candidate.simpleName }.toSet()
    def jackson3TypeNames = jackson3Classes.findAll { JavaClass candidate ->
      isSupportedTopLevelAdapterType(candidate)
    }.collect { JavaClass candidate -> candidate.simpleName }.toSet()

    expect:
    commonContractNames.intersect(jackson3TypeNames).isEmpty()
  }

  def "jackson3 supported public signatures do not expose shared internal types"() {
    given:
    def violations = jackson3Classes.findAll { JavaClass candidate ->
      isSupportedAdapterType(candidate)
    }.collectMany { JavaClass candidate ->
      exposedTypes(candidate)
          .findAll { JavaClass dependency -> isSharedInternalType(dependency) }
          .collect { JavaClass dependency -> "${candidate.fullName} -> ${dependency.fullName}" }
    }

    expect:
    assert violations.isEmpty(), violations.join(System.lineSeparator())
  }

  def "shared passive fixtures outside the contract package depend only on allowed packages"() {
    expect:
    classes()
        .that()
        .resideInAPackage("com.kazforge.jsonapi.fixtures..")
        .and()
        .resideOutsideOfPackage("com.kazforge.jsonapi.fixtures.contract..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
        "java..",
        "org.jspecify.annotations..",
        "com.kazforge.jsonapi.annotation..",
        "com.kazforge.jsonapi.core.model..",
        "com.kazforge.jsonapi.jackson..",
        "com.kazforge.jsonapi.fixtures..",
        "com.fasterxml.jackson.annotation..")
        .check(sharedFixtureClasses)
  }

  def "shared contract-fixture carriers depend only on allowed application-shaped packages"() {
    expect:
    classes()
        .that()
        .resideInAPackage("com.kazforge.jsonapi.fixtures.contract..")
        .and()
        .haveSimpleNameNotEndingWith("CharacterizationSpec")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
        "java..",
        "org.jspecify.annotations..",
        "com.kazforge.jsonapi.annotation..",
        "com.kazforge.jsonapi.core.model..",
        "com.kazforge.jsonapi.jackson..",
        "com.kazforge.jsonapi.fixtures..",
        "com.fasterxml.jackson.annotation..")
        .check(sharedFixtureClasses)
  }

  def "shared characterization contract specs depend only on allowed contract packages"() {
    expect:
    classes()
        .that()
        .resideInAPackage("com.kazforge.jsonapi.fixtures.contract..")
        .and()
        .haveSimpleNameEndingWith("CharacterizationSpec")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
        "java..",
        "groovy..",
        "org.codehaus.groovy..",
        "spock..",
        "org.spockframework..",
        "org.jspecify.annotations..",
        "com.kazforge.jsonapi.annotation..",
        "com.kazforge.jsonapi.core.model..",
        "com.kazforge.jsonapi.jackson..",
        "com.kazforge.jsonapi.fixtures..",
        "com.fasterxml.jackson.annotation..")
        .check(sharedFixtureClasses)
  }

  private static boolean isSupportedCommonType(JavaClass candidate) {
    candidate.topLevelClass &&
        candidate.modifiers.contains(JavaModifier.PUBLIC) &&
        (candidate.packageName == "com.kazforge.jsonapi.jackson" ||
        (candidate.packageName.startsWith("com.kazforge.jsonapi.jackson.") &&
        !isInternalPackage(candidate.packageName)))
  }

  private static boolean isSupportedAdapterType(JavaClass candidate) {
    candidate.modifiers.contains(JavaModifier.PUBLIC) &&
        (candidate.packageName == "com.kazforge.jsonapi.jackson3" ||
        (candidate.packageName.startsWith("com.kazforge.jsonapi.jackson3.") &&
        !isAdapterInternalPackage(candidate.packageName)))
  }

  private static boolean isSupportedTopLevelAdapterType(JavaClass candidate) {
    candidate.topLevelClass && isSupportedAdapterType(candidate)
  }

  private static boolean isInternalPackage(String packageName) {
    packageName == "com.kazforge.jsonapi.jackson.internal" ||
        packageName.startsWith("com.kazforge.jsonapi.jackson.internal.")
  }

  private static boolean isAdapterInternalPackage(String packageName) {
    packageName == "com.kazforge.jsonapi.jackson3.internal" ||
        packageName.startsWith("com.kazforge.jsonapi.jackson3.internal.")
  }

  private static boolean isSharedInternalType(JavaClass candidate) {
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

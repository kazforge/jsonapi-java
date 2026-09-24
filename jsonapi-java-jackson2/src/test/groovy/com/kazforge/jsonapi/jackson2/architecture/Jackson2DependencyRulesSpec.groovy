package com.kazforge.jsonapi.jackson2.architecture

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.kazforge.jsonapi.mapping.internal.ArchitectureMappingInternalFixture

import spock.lang.Shared
import spock.lang.Specification

class Jackson2DependencyRulesSpec extends Specification {

  @Shared
  JavaClasses jackson2Classes = new ClassFileImporter()
  .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
  .importPackages("com.kazforge.jsonapi.jackson2..")

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
  "com.kazforge.jsonapi.diagnostic..")

  @Shared
  JavaClasses sharedFixtureClasses = new ClassFileImporter()
  .importPackages("com.kazforge.jsonapi.fixtures..")

  def "jackson2 production types depend only on allowed packages"() {
    expect:
    classes()
        .that()
        .resideInAPackage("com.kazforge.jsonapi.jackson2..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
        "java..",
        "org.jspecify.annotations..",
        "com.kazforge.jsonapi.core.aggregate..",
        "com.kazforge.jsonapi.core.model..",
        "com.kazforge.jsonapi.core.validation..",
        "com.kazforge.jsonapi.annotation..",
        "com.kazforge.jsonapi",
        "com.kazforge.jsonapi.api..",
        "com.kazforge.jsonapi.document..",
        "com.kazforge.jsonapi.mapping..",
        "com.kazforge.jsonapi.patch..",
        "com.kazforge.jsonapi.representation..",
        "com.kazforge.jsonapi.diagnostic..",
        "com.kazforge.jsonapi.jackson2..",
        "com.fasterxml.jackson..")
        .check(jackson2Classes)
  }

  def "jackson2 responsibility selectors are non-empty and disjoint"() {
    given:
    def root = jackson2Classes.findAll { JavaClass candidate ->
      candidate.packageName == "com.kazforge.jsonapi.jackson2"
    }
    def mapping = jackson2Classes.findAll { JavaClass candidate ->
      candidate.packageName == "com.kazforge.jsonapi.jackson2.mapping" ||
          candidate.packageName.startsWith("com.kazforge.jsonapi.jackson2.mapping.")
    }
    def internal = jackson2Classes.findAll { JavaClass candidate ->
      candidate.packageName == "com.kazforge.jsonapi.jackson2.internal"
    }
    def codec = jackson2Classes.findAll { JavaClass candidate ->
      candidate.packageName == "com.kazforge.jsonapi.jackson2.internal.codec" ||
          candidate.packageName.startsWith("com.kazforge.jsonapi.jackson2.internal.codec.")
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

  def "jackson2 mapping contracts do not depend on composition or internals"() {
    expect:
    noClasses()
        .that()
        .resideInAPackage("com.kazforge.jsonapi.jackson2.mapping..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
        "com.kazforge.jsonapi.jackson2",
        "com.kazforge.jsonapi.jackson2.internal",
        "com.kazforge.jsonapi.jackson2.internal.codec..")
        .check(jackson2Classes)
  }

  def "jackson2 codec does not depend on composition, mapping, or exact internal"() {
    expect:
    noClasses()
        .that()
        .resideInAPackage("com.kazforge.jsonapi.jackson2.internal.codec..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
        "com.kazforge.jsonapi.jackson2",
        "com.kazforge.jsonapi.jackson2.mapping..",
        "com.kazforge.jsonapi.jackson2.internal")
        .check(jackson2Classes)
  }

  def "jackson2 exact internal does not depend on composition or codec"() {
    expect:
    noClasses()
        .that()
        .resideInAPackage("com.kazforge.jsonapi.jackson2.internal")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
        "com.kazforge.jsonapi.jackson2",
        "com.kazforge.jsonapi.jackson2.internal.codec..")
        .check(jackson2Classes)
  }

  def "jackson2 exposes no duplicate public common contract types"() {
    given:
    def commonContractNames = commonClasses.findAll { JavaClass candidate ->
      isSupportedCommonType(candidate)
    }.collect { JavaClass candidate -> candidate.simpleName }.toSet()
    def jackson2TypeNames = jackson2Classes.findAll { JavaClass candidate ->
      isSupportedTopLevelAdapterType(candidate)
    }.collect { JavaClass candidate -> candidate.simpleName }.toSet()

    expect:
    commonContractNames.intersect(jackson2TypeNames).isEmpty()
  }

  def "supported common type selector matches known neutral contract types"() {
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
      candidate != null && isSupportedCommonType(candidate)
    }
  }

  def "supported common type selector excludes mapping implementation types"() {
    given:
    def fixtureClasses = new ClassFileImporter()
        .importClasses(ArchitectureMappingInternalFixture)

    expect:
    fixtureClasses.every { JavaClass candidate -> !isSupportedCommonType(candidate) }
  }

  def "jackson2 supported public signatures do not expose shared internal types"() {
    given:
    def violations = jackson2Classes.findAll { JavaClass candidate ->
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
        "com.kazforge.jsonapi",
        "com.kazforge.jsonapi.api..",
        "com.kazforge.jsonapi.document..",
        "com.kazforge.jsonapi.mapping..",
        "com.kazforge.jsonapi.patch..",
        "com.kazforge.jsonapi.representation..",
        "com.kazforge.jsonapi.diagnostic..",
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
        "com.kazforge.jsonapi",
        "com.kazforge.jsonapi.api..",
        "com.kazforge.jsonapi.document..",
        "com.kazforge.jsonapi.mapping..",
        "com.kazforge.jsonapi.patch..",
        "com.kazforge.jsonapi.representation..",
        "com.kazforge.jsonapi.diagnostic..",
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
        "com.kazforge.jsonapi",
        "com.kazforge.jsonapi.api..",
        "com.kazforge.jsonapi.document..",
        "com.kazforge.jsonapi.mapping..",
        "com.kazforge.jsonapi.patch..",
        "com.kazforge.jsonapi.representation..",
        "com.kazforge.jsonapi.diagnostic..",
        "com.kazforge.jsonapi.fixtures..",
        "com.fasterxml.jackson.annotation..")
        .check(sharedFixtureClasses)
  }

  private static boolean isSupportedCommonType(JavaClass candidate) {
    candidate.topLevelClass &&
        candidate.modifiers.contains(JavaModifier.PUBLIC) &&
        (candidate.packageName == "com.kazforge.jsonapi" ||
        (isNeutralContractPackage(candidate.packageName) &&
        !isInternalPackage(candidate.packageName) &&
        !isMappingInternalPackage(candidate.packageName)))
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

  private static boolean isSupportedAdapterType(JavaClass candidate) {
    candidate.modifiers.contains(JavaModifier.PUBLIC) &&
        (candidate.packageName == "com.kazforge.jsonapi.jackson2" ||
        (candidate.packageName.startsWith("com.kazforge.jsonapi.jackson2.") &&
        !isAdapterInternalPackage(candidate.packageName)))
  }

  private static boolean isSupportedTopLevelAdapterType(JavaClass candidate) {
    candidate.topLevelClass && isSupportedAdapterType(candidate)
  }

  private static boolean isInternalPackage(String packageName) {
    packageName == "com.kazforge.jsonapi.internal" ||
        packageName.startsWith("com.kazforge.jsonapi.internal.")
  }

  private static boolean isAdapterInternalPackage(String packageName) {
    packageName == "com.kazforge.jsonapi.jackson2.internal" ||
        packageName.startsWith("com.kazforge.jsonapi.jackson2.internal.")
  }

  private static boolean isSharedInternalType(JavaClass candidate) {
    isInternalPackage(candidate.packageName) || isMappingInternalPackage(candidate.packageName)
  }

  private static boolean isMappingInternalPackage(String packageName) {
    packageName == "com.kazforge.jsonapi.mapping.internal" ||
        packageName.startsWith("com.kazforge.jsonapi.mapping.internal.")
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

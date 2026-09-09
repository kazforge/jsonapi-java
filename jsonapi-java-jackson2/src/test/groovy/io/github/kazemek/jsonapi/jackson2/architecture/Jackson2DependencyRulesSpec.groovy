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
      isSupportedCommonType(candidate)
    }.collect { JavaClass candidate -> candidate.simpleName }.toSet()
    def jackson2TypeNames = jackson2Classes.findAll { JavaClass candidate ->
      isSupportedTopLevelAdapterType(candidate)
    }.collect { JavaClass candidate -> candidate.simpleName }.toSet()

    expect:
    commonContractNames.intersect(jackson2TypeNames).isEmpty()
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

  private static boolean isSupportedCommonType(JavaClass candidate) {
    candidate.topLevelClass &&
        candidate.modifiers.contains(JavaModifier.PUBLIC) &&
        (candidate.packageName == "io.github.kazemek.jsonapi.jackson" ||
        (candidate.packageName.startsWith("io.github.kazemek.jsonapi.jackson.") &&
        !isInternalPackage(candidate.packageName)))
  }

  private static boolean isSupportedAdapterType(JavaClass candidate) {
    candidate.modifiers.contains(JavaModifier.PUBLIC) &&
        (candidate.packageName == "io.github.kazemek.jsonapi.jackson2" ||
        (candidate.packageName.startsWith("io.github.kazemek.jsonapi.jackson2.") &&
        !isAdapterInternalPackage(candidate.packageName)))
  }

  private static boolean isSupportedTopLevelAdapterType(JavaClass candidate) {
    candidate.topLevelClass && isSupportedAdapterType(candidate)
  }

  private static boolean isInternalPackage(String packageName) {
    packageName == "io.github.kazemek.jsonapi.jackson.internal" ||
        packageName.startsWith("io.github.kazemek.jsonapi.jackson.internal.")
  }

  private static boolean isAdapterInternalPackage(String packageName) {
    packageName == "io.github.kazemek.jsonapi.jackson2.internal" ||
        packageName.startsWith("io.github.kazemek.jsonapi.jackson2.internal.")
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
    }
    candidate.methods.findAll { isExposedMember(it) }.each { member ->
      types.addAll(member.allInvolvedRawTypes)
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

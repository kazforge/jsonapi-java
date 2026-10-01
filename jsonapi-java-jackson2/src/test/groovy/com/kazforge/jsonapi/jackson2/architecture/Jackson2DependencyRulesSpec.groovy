package com.kazforge.jsonapi.jackson2.architecture

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaCodeUnit
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.kazforge.jsonapi.jackson2.ArchitectureAdapterSignatureLeakFixture
import com.kazforge.jsonapi.jackson2.internal.codec.ArchitectureAdapterInternalException
import com.kazforge.jsonapi.mapping.internal.PropertyRole
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

  def "jackson2 responsibility selectors are non-empty"() {
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
      candidate.topLevelClass && isSupportedAdapterType(candidate)
    }.collect { JavaClass candidate -> candidate.simpleName }.toSet()

    expect:
    !commonContractNames.isEmpty()
    !jackson2TypeNames.isEmpty()
    commonContractNames.intersect(jackson2TypeNames).isEmpty()
  }

  def "jackson2 supported public signatures do not expose internal types"() {
    given:
    def supportedTypes = jackson2Classes.findAll { JavaClass candidate ->
      isSupportedAdapterType(candidate)
    }
    def violations = supportedTypes.collectMany { JavaClass candidate ->
      exposedTypes(candidate)
          .findAll { JavaClass dependency -> isUnsupportedType(dependency) }
          .collect { JavaClass dependency -> "${candidate.fullName} -> ${dependency.fullName}" }
    }

    expect:
    !supportedTypes.isEmpty()
    assert violations.isEmpty(), violations.join(System.lineSeparator())
  }

  def "jackson2 signature scan detects declared internal exceptions and generic mapping internals"() {
    given:
    def fixtureClasses = new ClassFileImporter()
        .importClasses(
        ArchitectureAdapterSignatureLeakFixture,
        ArchitectureAdapterInternalException,
        PropertyRole)
    def fixture = fixtureClasses.get(ArchitectureAdapterSignatureLeakFixture)
    def internalException = fixtureClasses.get(ArchitectureAdapterInternalException)
    def mappingInternal = fixtureClasses.get(PropertyRole)

    expect:
    isSupportedAdapterType(fixture)
    exposedTypes(fixture.getMethod("leaksAdapterInternalException"))
        .findAll { isUnsupportedType(it) }.toSet() == [internalException].toSet()
    exposedTypes(fixture.getConstructor())
        .findAll { isUnsupportedType(it) }.toSet() == [internalException].toSet()
    exposedTypes(fixture.getMethod("leaksMappingInternalArgument"))
        .findAll { isUnsupportedType(it) }.toSet() == [mappingInternal].toSet()
  }

  private static boolean isSupportedCommonType(JavaClass candidate) {
    candidate.topLevelClass &&
        candidate.modifiers.contains(JavaModifier.PUBLIC) &&
        (candidate.packageName == "com.kazforge.jsonapi" ||
        (isNeutralContractPackage(candidate.packageName) &&
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

  private static boolean isAdapterInternalPackage(String packageName) {
    packageName == "com.kazforge.jsonapi.jackson2.internal" ||
        packageName.startsWith("com.kazforge.jsonapi.jackson2.internal.")
  }

  private static boolean isUnsupportedType(JavaClass candidate) {
    isMappingInternalPackage(candidate.packageName) ||
        isAdapterInternalPackage(candidate.packageName)
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
      types.addAll(exposedTypes(member))
    }
    candidate.methods.findAll { isExposedMember(it) }.each { member ->
      types.addAll(exposedTypes(member))
    }
    candidate.fields.findAll { isExposedMember(it) }.each { member ->
      types.addAll(member.allInvolvedRawTypes)
    }
    types
  }

  private static Set<JavaClass> exposedTypes(JavaCodeUnit member) {
    def types = new LinkedHashSet<JavaClass>(member.allInvolvedRawTypes)
    types.addAll(member.exceptionTypes)
    types
  }

  private static boolean isExposedMember(member) {
    member.modifiers.contains(JavaModifier.PUBLIC) ||
        member.modifiers.contains(JavaModifier.PROTECTED)
  }
}

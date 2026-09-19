package com.kazforge.jsonapi.gsonpoc

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

import com.tngtech.archunit.core.importer.ClassFileImporter
import spock.lang.Specification

class GsonPoCArchitectureSpec extends Specification {

  def "Gson fitness test does not depend on concrete Jackson libraries or adapters"() {
    given:
    def classes = new ClassFileImporter().importPackages("com.kazforge.jsonapi.gsonpoc..")

    expect:
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
        "com.fasterxml.jackson..",
        "tools.jackson..",
        "com.kazforge.jsonapi.jackson2..",
        "com.kazforge.jsonapi.jackson3..")
        .check(classes)
  }
}

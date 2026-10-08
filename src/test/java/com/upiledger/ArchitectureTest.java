package com.upiledger;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.upiledger")
class ArchitectureTest {

    @ArchTest
    static final ArchRule no_module_should_depend_on_test_code =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..test..");
}

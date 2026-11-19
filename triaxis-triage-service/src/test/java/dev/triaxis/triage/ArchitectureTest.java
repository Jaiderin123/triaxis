package dev.triaxis.triage;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "dev.triaxis.triage", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule layers = layeredArchitecture()
			.consideringOnlyDependenciesInLayers()
			.withOptionalLayers(true)
			.layer("Domain").definedBy("dev.triaxis.triage.domain..")
			.layer("Application").definedBy("dev.triaxis.triage.application..")
			.layer("Infrastructure").definedBy("dev.triaxis.triage.infrastructure..")
			.whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
			.whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
			.whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer();

	@ArchTest
	static final ArchRule domainIsFrameworkFree = noClasses()
			.that().resideInAPackage("dev.triaxis.triage.domain..")
			.should().dependOnClassesThat().resideInAnyPackage(
					"org.springframework..", "reactor..", "io.r2dbc..", "org.apache.kafka..", "com.fasterxml..")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule applicationIsFrameworkFree = noClasses()
			.that().resideInAPackage("dev.triaxis.triage.application..")
			.should().dependOnClassesThat().resideInAnyPackage(
					"org.springframework..", "io.r2dbc..", "org.apache.kafka..", "com.fasterxml..")
			.allowEmptyShould(true);

}

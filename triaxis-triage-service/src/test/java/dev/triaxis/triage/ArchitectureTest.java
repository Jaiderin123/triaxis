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

	/** Time comes from the clock port (CLAUDE.md): the core never reads the system clock. */
	@ArchTest
	static final ArchRule coreNeverReadsTheSystemClock = noClasses()
			.that().resideInAnyPackage("dev.triaxis.triage.domain..", "dev.triaxis.triage.application..")
			.should().callMethod(java.time.Instant.class, "now")
			.orShould().callMethod(java.time.OffsetDateTime.class, "now")
			.orShould().callMethod(java.time.LocalDateTime.class, "now")
			.orShould().callMethod(java.time.Clock.class, "systemUTC")
			.allowEmptyShould(true);

	/** Web adapters are functional endpoints (ADR 0012). */
	@ArchTest
	static final ArchRule noAnnotatedControllers = noClasses()
			.should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
			.orShould().beAnnotatedWith("org.springframework.stereotype.Controller")
			.allowEmptyShould(true);

}

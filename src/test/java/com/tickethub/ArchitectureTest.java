package com.tickethub;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import jakarta.persistence.Entity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Function;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Межі модульного моноліту, перевірені на байткоді: якщо хтось піде в чужий репозиторій «бо так швидше»,
 * збірка впаде, а не код-рев'ю пропустить.
 */
@AnalyzeClasses(packages = "com.tickethub", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final List<String> MODULES = List.of("user", "venue", "event", "booking", "payment", "ticket");

    @ArchTest
    static final ArchRule modulesHaveNoCycles = slices()
            .matching("com.tickethub.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule commonKnowsNothingAboutModules = noClasses()
            .that().resideInAPackage("com.tickethub.common..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    MODULES.stream().map(module -> "com.tickethub." + module + "..").toArray(String[]::new));

    /** Чужі дані — лише через сервіс модуля-власника, який тримає інваріанти (статуси місць, блокування). */
    @ArchTest
    static final ArchRule repositoriesArePrivateToTheirModule = forEachModule(module -> noClasses()
            .that().resideOutsideOfPackage("com.tickethub." + module + "..")
            .should().dependOnClassesThat().resideInAPackage("com.tickethub." + module + ".repository..")
            .because("модуль " + module + " відкриває свої дані лише через сервіси"));

    /** HTTP-шар модуля — його внутрішня справа; інші модулі не викликають чужі контролери. */
    @ArchTest
    static final ArchRule controllersArePrivateToTheirModule = forEachModule(module -> noClasses()
            .that().resideOutsideOfPackage("com.tickethub." + module + "..")
            .should().dependOnClassesThat().resideInAPackage("com.tickethub." + module + ".api"));

    @ArchTest
    static final ArchRule repositoriesAreUsedOnlyByServices = classes()
            .that().resideInAPackage("com.tickethub.*.repository..")
            .should().onlyBeAccessed().byAnyPackage("..service..", "..repository..");

    @ArchTest
    static final ArchRule domainDoesNotDependOnUpperLayers = noClasses()
            .that().resideInAPackage("com.tickethub.*.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..service..", "..repository..");

    @ArchTest
    static final ArchRule controllersLiveInApi = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("com.tickethub.*.api");

    @ArchTest
    static final ArchRule entitiesLiveInDomain = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAnyPackage("com.tickethub.*.domain", "com.tickethub.common.outbox");

    @ArchTest
    static final ArchRule noFieldInjection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule noJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    @ArchTest
    static final ArchRule noStandardStreams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    private static ArchRule forEachModule(Function<String, ArchRule> rule) {
        return CompositeArchRule.of(MODULES.stream().map(rule).toList());
    }
}

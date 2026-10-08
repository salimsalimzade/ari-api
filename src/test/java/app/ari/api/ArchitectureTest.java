package app.ari.api;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The layer rules from SKILL.md section 4, checked on every build. */
@AnalyzeClasses(packages = "app.ari.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule layersDependInOneDirection =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Presentation")
          .definedBy("..feature.*.presentation..")
          .layer("Application")
          .definedBy("..feature.*.application..")
          .layer("Domain")
          .definedBy("..feature.*.domain..")
          .layer("Infrastructure")
          .definedBy("..feature.*.infrastructure..")
          .whereLayer("Presentation")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Application")
          .mayOnlyBeAccessedByLayers("Presentation")
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers("Application", "Infrastructure")
          .whereLayer("Infrastructure")
          .mayNotBeAccessedByAnyLayer();

  @ArchTest
  static final ArchRule featuresUseEachOtherOnlyThroughFacades =
      slices()
          .matching("app.ari.api.feature.(*)..")
          .should()
          .notDependOnEachOther()
          .ignoreDependency(
              alwaysTrue(), resideInAnyPackage("..application.facade..", "..application.model.."));
}

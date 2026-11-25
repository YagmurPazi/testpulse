package io.github.yagmurpazi.testpulse.analysis;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.github.yagmurpazi.testpulse.architecture.HexagonalArchitecture;

@AnalyzeClasses(packagesOf = AnalysisApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule hexagonalArchitecture =
            HexagonalArchitecture.rulesFor(AnalysisApplication.class.getPackageName());
}

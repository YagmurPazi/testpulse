package io.github.yagmurpazi.testpulse.ingestion;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.github.yagmurpazi.testpulse.architecture.HexagonalArchitecture;

@AnalyzeClasses(packagesOf = IngestionApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule HEXAGONAL_ARCHITECTURE =
            HexagonalArchitecture.rulesFor(IngestionApplication.class.getPackageName());
}

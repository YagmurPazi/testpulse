package io.github.yagmurpazi.testpulse.notification;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.github.yagmurpazi.testpulse.architecture.HexagonalArchitecture;

@AnalyzeClasses(packagesOf = NotificationApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule hexagonalArchitecture =
            HexagonalArchitecture.rulesFor(NotificationApplication.class.getPackageName());
}

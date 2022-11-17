package stroom.config;

import stroom.config.app.AppConfig;
import stroom.data.shared.StreamTypeNames;
import stroom.meta.impl.MetaServiceConfig;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.config.AppConfigValidator;
import stroom.util.config.ConfigValidator;
import stroom.util.config.ConfigValidator.Result;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;

public class TestAppConfigValidator extends AbstractCoreIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestAppConfigValidator.class);

    @Inject
    private AppConfig appConfig;
    @Inject
    private AppConfigValidator appConfigValidator;

    @Test
    void testValidate_noErrors() {
        // Make sure we can validate the whole tree
        ConfigValidator.Result<AbstractConfig> result = appConfigValidator.validateRecursively(appConfig);

        Assertions.assertThat(result.getErrorCount()).isEqualTo(0);
    }

    /**
     * Test validation methods that are not bean props, i.e. cross prop validation
     */
    @Test
    void testCrossPropValidation() {
        final Set<String> types = new HashSet<>(StreamTypeNames.ALL_HARD_CODED_STREAM_TYPE_NAMES);
        final Set<String> rawTypes = new HashSet<>(StreamTypeNames.ALL_HARD_CODED_RAW_STREAM_TYPE_NAMES);
        // Add a raw type that is not in the set of types so should fail validation
        rawTypes.add("foo");
        MetaServiceConfig metaServiceConfig = new MetaServiceConfig(
                null, null, null, null, null,
                types,
                rawTypes);

        final Result<AbstractConfig> result = appConfigValidator.validate(metaServiceConfig);
        result.handleViolations((violation, severity) -> LOGGER.error("Got {} violation {}",
                severity, violation.getMessage()));
        Assertions.assertThat(result.getErrorCount())
                .isEqualTo(1);
    }
}

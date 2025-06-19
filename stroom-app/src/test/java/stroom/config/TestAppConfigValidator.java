package stroom.config;

import stroom.config.app.AppConfig;
import stroom.data.shared.StreamTypeNames;
import stroom.meta.impl.MetaServiceConfig;
import stroom.meta.shared.DataFormatNames;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.config.AppConfigValidator;
import stroom.util.config.ConfigValidator;
import stroom.util.config.ConfigValidator.Result;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;

import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

public class TestAppConfigValidator extends AbstractCoreIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestAppConfigValidator.class);

    @Inject
    private AppConfig appConfig;
    @Inject
    private AppConfigValidator appConfigValidator;

    @Test
    void testValidate_noErrors() {
        // Make sure we can validate the whole tree
        final ConfigValidator.Result<AbstractConfig> result = appConfigValidator.validateRecursively(appConfig);

        result.handleViolations((constraintViolation, validationSeverity) -> {
            switch (validationSeverity) {
                case ERROR -> LOGGER.error("Got violation: {}", constraintViolation.getMessage());
                case WARNING -> LOGGER.warn("Got violation: {}", constraintViolation.getMessage());
            }
        });

        Assertions.assertThat(result.getErrorCount())
                .isEqualTo(0);
    }

    /**
     * Test validation methods that are not bean props, i.e. cross prop validation
     */
    @Test
    void testCrossPropValidation() {
        final Set<String> types = new HashSet<>(StreamTypeNames.ALL_HARD_CODED_STREAM_TYPE_NAMES);
        final Set<String> rawTypes = new HashSet<>(StreamTypeNames.ALL_HARD_CODED_RAW_STREAM_TYPE_NAMES);
        final Set<String> dataFormats = new HashSet<>(DataFormatNames.ALL_HARD_CODED_FORMAT_NAMES);
        // Add a raw type that is not in the set of types so should fail validation
        rawTypes.add("foo");
        final MetaServiceConfig metaServiceConfig = new MetaServiceConfig(
                null,
                null,
                null,
                null,
                null,
                types,
                rawTypes,
                dataFormats,
                0);

        final Result<AbstractConfig> result = appConfigValidator.validate(metaServiceConfig);

        result.handleViolations((constraintViolation, validationSeverity) -> {
            switch (validationSeverity) {
                case ERROR -> LOGGER.error("Got violation: {}", constraintViolation.getMessage());
                case WARNING -> LOGGER.warn("Got violation: {}", constraintViolation.getMessage());
            }
        });
        Assertions.assertThat(result.getErrorCount())
                .isEqualTo(1);
    }
}

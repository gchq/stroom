package stroom.config;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import stroom.config.app.AppConfig;
import stroom.config.global.impl.validation.ConfigValidator;
import stroom.test.AbstractCoreIntegrationTest;

import javax.inject.Inject;

public class TestConfigValidator extends AbstractCoreIntegrationTest {

    @Inject
    private AppConfig appConfig;
    @Inject
    private ConfigValidator configValidator;

    @Test
    void testValidate_oneError() {
        // You may see errors in the log due to the lack of a node name,
        // but they are unrelated to this test
        appConfig.getNodeConfig().setNodeName(null);

        ConfigValidator.Result result = configValidator.validate(appConfig);

        Assertions.assertThat(result.getErrorCount()).isEqualTo(1);
    }

    @Test
    void testValidate_noErrors() {
        ConfigValidator.Result result = configValidator.validate(appConfig);

        Assertions.assertThat(result.getErrorCount()).isEqualTo(0);
    }

    @Test
    void testValidation_badRegex() {
        appConfig.getUiConfig().setNamePattern("(bad regex");

        ConfigValidator.Result result = configValidator.validate(appConfig);

        Assertions.assertThat(result.getErrorCount()).isEqualTo(1);
    }

    @Test
    void testValidation_goodRegex() {
        appConfig.getUiConfig().setNamePattern("(good regex)?");

        ConfigValidator.Result result = configValidator.validate(appConfig);

        Assertions.assertThat(result.getErrorCount()).isEqualTo(0);
    }

}

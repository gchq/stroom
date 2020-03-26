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
    void testValidate_noErrors() {
        // Make sure we can validate the whole tree
        ConfigValidator.Result result = configValidator.validateRecursively(appConfig);

        Assertions.assertThat(result.getErrorCount()).isEqualTo(0);
    }

}

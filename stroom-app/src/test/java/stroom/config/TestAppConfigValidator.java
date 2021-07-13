package stroom.config;

import stroom.config.app.AppConfig;
import stroom.util.config.AppConfigValidator;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.config.ConfigValidator;
import stroom.util.shared.AbstractConfig;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

public class TestAppConfigValidator extends AbstractCoreIntegrationTest {

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

}

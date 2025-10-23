package stroom.analytics.impl;

import stroom.ui.config.shared.AnalyticUiDefaultConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestAnalyticsServiceImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestAnalyticsServiceImpl.class);

    @Mock
    private EmailSender mockEmailSender;

    final RuleEmailTemplatingService templatingService = new RuleEmailTemplatingService();

    /**
     * Verify that our example template from config works with our example detection
     */
    @Test
    void testExampleDetection() {
        final AnalyticsServiceImpl analyticsService = new AnalyticsServiceImpl(
                mockEmailSender,
                templatingService,
                null,
                null,
                null,
                null);

        final Detection exampleDetection = analyticsService.getExampleDetection();
        final String template = new AnalyticUiDefaultConfig().getDefaultBodyTemplate();
        final String output = templatingService.renderTemplate(exampleDetection, template);

        LOGGER.info("output:\n{}", output);

        assertThat(output)
                .contains("<li><strong>name_1</strong>: value_A</li>")
                .contains("<li><strong>name_4</strong>: </li>")
                .contains("<li>Environment: Test Environment, Stream ID: 1001, Event ID: 2</li>");
    }
}

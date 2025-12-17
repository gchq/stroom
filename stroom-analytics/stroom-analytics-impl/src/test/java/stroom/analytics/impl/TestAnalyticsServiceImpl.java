/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

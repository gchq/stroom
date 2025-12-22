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

import stroom.analytics.shared.NotificationEmailDestination;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestEmailSender {

    @Mock
    private AnalyticsConfig mockAnalyticsConfig;

    private final RuleEmailTemplatingService ruleEmailTemplatingService = new RuleEmailTemplatingService();

    @Disabled // manual only due to reliance on smtp.freesmtpservers.com
    @Test
    void send() {
        // See https://www.wpoven.com/tools/free-smtp-server-for-testing to check the email sent
        Mockito.when(mockAnalyticsConfig.getEmailConfig())
                .thenReturn(new EmailConfig(
                        SmtpConfig.unauthenticated("smtp.freesmtpservers.com", 25),
                        "foo@foo.com",
                        "Mr Foo"));

        final EmailSender emailSender = new EmailSender(() ->
                mockAnalyticsConfig, ruleEmailTemplatingService);

        final NotificationEmailDestination destination = NotificationEmailDestination.builder()
                .to("bar@bar.com")
                .subjectTemplate("{{ headline }}")
                .bodyTemplate("Detector {{ detectorName }} spotted something")
                .build();

        final Detection detection = Detection.builder()
                .withRandomDetectionUniqueId()
                .withRandomDetectorUuid()
                .withHeadline("Headline")
                .withDetectorName("DetectorName")
                .build();

        emailSender.sendDetection(destination, detection);
    }
}

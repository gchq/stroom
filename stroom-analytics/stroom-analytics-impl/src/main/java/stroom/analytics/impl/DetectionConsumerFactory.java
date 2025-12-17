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

import stroom.analytics.api.NotificationState;
import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.NotificationConfig;
import stroom.analytics.shared.NotificationDestinationType;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.analytics.shared.NotificationStreamDestination;
import stroom.pipeline.ErrorWriterProxy;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ElementId;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

public class DetectionConsumerFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DetectionConsumerFactory.class);

    private final Provider<DetectionsWriter> detectionsWriterProvider;
    private final Provider<EmailSender> emailSenderProvider;
    private final Provider<ErrorWriterProxy> errorWriterProxyProvider;
    private final NotificationStateService notificationStateService;

    @Inject
    public DetectionConsumerFactory(final Provider<DetectionsWriter> detectionsWriterProvider,
                                    final Provider<EmailSender> emailSenderProvider,
                                    final Provider<ErrorWriterProxy> errorWriterProxyProvider,
                                    final NotificationStateService notificationStateService) {
        this.detectionsWriterProvider = detectionsWriterProvider;
        this.emailSenderProvider = emailSenderProvider;
        this.errorWriterProxyProvider = errorWriterProxyProvider;
        this.notificationStateService = notificationStateService;
    }

    public Provider<DetectionConsumer> create(final AbstractAnalyticRuleDoc analyticRuleDoc) {
        if (analyticRuleDoc == null) {
            throw new NullPointerException("Null analytic rule doc.");
        }

        if (analyticRuleDoc.getNotifications() == null || analyticRuleDoc.getNotifications().isEmpty()) {
            throw new RuntimeException("No notification config found: " +
                                       RuleUtil.getRuleIdentity(analyticRuleDoc));
        }

        final List<NotificationConfig> notifications = analyticRuleDoc.getNotifications();
        if (notifications.size() == 1) {
            return () -> getDetectionConsumer(analyticRuleDoc, notifications.getFirst());

        } else {
            return () -> {
                final List<DetectionConsumer> consumers = notifications
                        .stream()
                        .map(notification -> getDetectionConsumer(analyticRuleDoc, notification))
                        .toList();

                return new DetectionConsumer() {
                    @Override
                    public void accept(final Detection detection) {
                        for (final DetectionConsumer detectionConsumer : consumers) {
                            try {
                                detectionConsumer.accept(detection);
                            } catch (final RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        }
                    }

                    @Override
                    public void start() {
                        for (final DetectionConsumer detectionConsumer : consumers) {
                            try {
                                detectionConsumer.start();
                            } catch (final RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        }
                    }

                    @Override
                    public void end() {
                        for (final DetectionConsumer detectionConsumer : consumers) {
                            try {
                                detectionConsumer.end();
                            } catch (final RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        }
                    }
                };
            };
        }
    }

    private DetectionConsumer getDetectionConsumer(final AbstractAnalyticRuleDoc analyticRuleDoc,
                                                   final NotificationConfig notificationConfig) {
        if (NotificationDestinationType.STREAM.equals(notificationConfig.getDestinationType())) {
            if (notificationConfig.getDestination() instanceof
                    final NotificationStreamDestination streamDestination) {
                final DetectionsWriter detectionsWriter = detectionsWriterProvider.get();
                detectionsWriter.setFeed(streamDestination.getDestinationFeed());
                return new DetectionConsumer() {
                    @Override
                    public void accept(final Detection detection) {
                        final NotificationState notificationState =
                                notificationStateService.getState(analyticRuleDoc, notificationConfig);
                        notificationState.enableIfPossible();
                        if (notificationState.incrementAndCheckEnabled()) {
                            detectionsWriter.accept(detection);
                        }
                    }

                    @Override
                    public void start() {
                        detectionsWriter.start();
                    }

                    @Override
                    public void end() {
                        detectionsWriter.end();
                    }
                };

            } else {
                throw new RuntimeException("No stream destination config found: " +
                                           RuleUtil.getRuleIdentity(analyticRuleDoc));
            }
        } else if (NotificationDestinationType.EMAIL.equals(notificationConfig.getDestinationType())) {
            if (notificationConfig.getDestination() instanceof
                    final NotificationEmailDestination emailDestination) {
                return new DetectionConsumer() {
                    @Override
                    public void accept(final Detection detection) {
                        final NotificationState notificationState =
                                notificationStateService.getState(analyticRuleDoc, notificationConfig);
                        notificationState.enableIfPossible();
                        if (notificationState.incrementAndCheckEnabled()) {
                            try {
                                emailSenderProvider.get().sendDetection(emailDestination, detection);
                            } catch (final RuntimeException e) {
                                errorWriterProxyProvider.get().log(
                                        Severity.ERROR,
                                        null,
                                        new ElementId(NotificationEmailDestination.class.getSimpleName()),
                                        e.getMessage());
                            }
                        }
                    }

                    @Override
                    public void start() {

                    }

                    @Override
                    public void end() {

                    }
                };

            } else {
                throw new RuntimeException("No email destination config found: " +
                                           RuleUtil.getRuleIdentity(analyticRuleDoc));
            }
        }

        return null;
    }
}

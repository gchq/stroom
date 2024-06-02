package stroom.analytics.impl;

import stroom.analytics.api.NotificationState;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.NotificationConfig;
import stroom.analytics.shared.NotificationDestinationType;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.analytics.shared.NotificationStreamDestination;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

public class DetectionConsumerFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DetectionConsumerFactory.class);

    private final Provider<DetectionsWriter> detectionsWriterProvider;
    private final Provider<EmailSender> emailSenderProvider;
    private final NotificationStateService notificationStateService;

    @Inject
    public DetectionConsumerFactory(final Provider<DetectionsWriter> detectionsWriterProvider,
                                    final Provider<EmailSender> emailSenderProvider,
                                    final NotificationStateService notificationStateService) {
        this.detectionsWriterProvider = detectionsWriterProvider;
        this.emailSenderProvider = emailSenderProvider;
        this.notificationStateService = notificationStateService;
    }

    public Provider<DetectionConsumer> create(final AnalyticRuleDoc analyticRuleDoc) {
        if (analyticRuleDoc == null) {
            throw new NullPointerException("Null analytic rule doc.");
        }

        if (analyticRuleDoc.getNotifications() == null || analyticRuleDoc.getNotifications().isEmpty()) {
            throw new RuntimeException("No notification config found: " +
                    AnalyticUtil.getAnalyticRuleIdentity(analyticRuleDoc));
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

    private DetectionConsumer getDetectionConsumer(final AnalyticRuleDoc analyticRuleDoc,
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
                        AnalyticUtil.getAnalyticRuleIdentity(analyticRuleDoc));
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
                            emailSenderProvider.get().send(emailDestination, detection);
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
                        AnalyticUtil.getAnalyticRuleIdentity(analyticRuleDoc));
            }
        }

        return null;
    }
}

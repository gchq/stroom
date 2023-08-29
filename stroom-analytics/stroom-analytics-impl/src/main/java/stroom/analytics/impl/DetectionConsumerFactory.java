package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticNotificationConfig;
import stroom.analytics.shared.AnalyticNotificationDestinationType;
import stroom.analytics.shared.AnalyticNotificationEmailDestination;
import stroom.analytics.shared.AnalyticNotificationStreamDestination;
import stroom.analytics.shared.AnalyticRuleDoc;

import javax.inject.Inject;
import javax.inject.Provider;

public class DetectionConsumerFactory {

    private final Provider<DetectionsWriter> detectionsWriterProvider;
    private final Provider<EmailSender> emailSenderProvider;

    @Inject
    public DetectionConsumerFactory(final Provider<DetectionsWriter> detectionsWriterProvider,
                                    final Provider<EmailSender> emailSenderProvider) {
        this.detectionsWriterProvider = detectionsWriterProvider;
        this.emailSenderProvider = emailSenderProvider;
    }

    public Provider<DetectionConsumer> create(final AnalyticRuleDoc analyticRuleDoc) {
        if (analyticRuleDoc == null) {
            throw new NullPointerException("Null analytic rule doc.");
        }

        final AnalyticNotificationConfig analyticNotificationConfig = analyticRuleDoc.getAnalyticNotificationConfig();
        if (analyticNotificationConfig == null) {
            throw new RuntimeException("No notification config found: " +
                    AnalyticUtil.getAnalyticRuleIdentity(analyticRuleDoc));
        }

        if (AnalyticNotificationDestinationType.STREAM.equals(analyticNotificationConfig.getDestinationType())) {
            if (analyticNotificationConfig.getDestination() instanceof
                    final AnalyticNotificationStreamDestination streamDestination) {
                return () -> {
                    final DetectionsWriter detectionsWriter = detectionsWriterProvider.get();
                    detectionsWriter.setFeed(streamDestination.getDestinationFeed());
                    return detectionsWriter;
                };

            } else {
                throw new RuntimeException("No stream destination config found: " +
                        AnalyticUtil.getAnalyticRuleIdentity(analyticRuleDoc));
            }
        } else if (AnalyticNotificationDestinationType.EMAIL.equals(analyticNotificationConfig.getDestinationType())) {
            if (analyticNotificationConfig.getDestination() instanceof
                    final AnalyticNotificationEmailDestination emailDestination) {
                return () -> new DetectionConsumer() {
                    @Override
                    public void accept(final Detection detection) {
                        emailSenderProvider.get()
                                .send(
                                        emailDestination.getEmailAddress(),
                                        emailDestination.getEmailAddress(),
                                        detection);
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

        throw new RuntimeException("No destination config found: " +
                AnalyticUtil.getAnalyticRuleIdentity(analyticRuleDoc));
    }
}

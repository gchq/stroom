package stroom.analytics.impl;

import stroom.analytics.api.AnalyticsService;
import stroom.analytics.rule.impl.AnalyticRuleStore;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.DuplicateCheckResource;
import stroom.analytics.shared.DuplicateNotificationConfig;
import stroom.analytics.shared.FetchColumnNamesResponse;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Message;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class AnalyticsServiceImpl implements AnalyticsService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticsServiceImpl.class);

    private final EmailSender emailSender;
    private final RuleEmailTemplatingService ruleEmailTemplatingService;
    private final ScheduledQueryAnalyticExecutor scheduledQueryAnalyticExecutor;
    private final AnalyticRuleStore analyticRuleStore;
    private final Provider<DuplicateCheckResource> duplicateCheckResourceProvider;
    private final Provider<DuplicateCheckService> duplicateCheckServiceProvider;

    @Inject
    AnalyticsServiceImpl(final EmailSender emailSender,
                         final RuleEmailTemplatingService ruleEmailTemplatingService,
                         final ScheduledQueryAnalyticExecutor scheduledQueryAnalyticExecutor,
                         final AnalyticRuleStore analyticRuleStore,
                         final Provider<DuplicateCheckResource> duplicateCheckResourceProvider,
                         final Provider<DuplicateCheckService> duplicateCheckServiceProvider) {
        this.emailSender = emailSender;
        this.ruleEmailTemplatingService = ruleEmailTemplatingService;
        this.scheduledQueryAnalyticExecutor = scheduledQueryAnalyticExecutor;
        this.analyticRuleStore = analyticRuleStore;
        this.duplicateCheckResourceProvider = duplicateCheckResourceProvider;
        this.duplicateCheckServiceProvider = duplicateCheckServiceProvider;
    }

    @Override
    public String testTemplate(final String template) {
        return ruleEmailTemplatingService.renderTemplate(getExampleDetection(), template);
    }

    @Override
    public void sendTestEmail(final NotificationEmailDestination emailDestination) {
        emailSender.sendDetection(emailDestination, getExampleDetection());
    }

    @Override
    public List<Message> validateChanges(final AnalyticRuleDoc analytic) {
        Objects.requireNonNull(analytic);
        final AnalyticRuleDoc currentAnalytic = analyticRuleStore.readDocument(analytic.asDocRef());
        if (currentAnalytic != null && requiresDupCheckStore(analytic)) {
            final Set<String> enabledNodeNames = duplicateCheckServiceProvider.get()
                    .getEnabledNodeNames(analytic.asDocRef());

            if (enabledNodeNames.size() > 1) {
                return Message.warning(
                        "There are enabled executors on multiple nodes. " +
                        "Duplicate checking is not supported when running on multiple nodes").asList();
            }

            final DuplicateNotificationConfig oldConfig = NullSafe.get(
                    currentAnalytic, AnalyticRuleDoc::getDuplicateNotificationConfig);
            // Must be non-null at this point
            final DuplicateNotificationConfig newConfig = analytic.getDuplicateNotificationConfig();

            // This is a minor optimisation to drop out quickly if the bit of the analytic that
            // impact column names are unchanged.
            if (Objects.equals(currentAnalytic.getQuery(), analytic.getQuery())) {
                // Identical queries
                if (newConfig.isChooseColumns()
                    && NullSafe.equalProperties(oldConfig, newConfig, DuplicateNotificationConfig::getColumnNames)
                    && NullSafe.equalProperties(oldConfig, newConfig, DuplicateNotificationConfig::isChooseColumns)) {
                    // Identical custom column lists
                    return Collections.emptyList();
                } else if (!newConfig.isChooseColumns()
                           && NullSafe.equalProperties(
                        oldConfig,
                        newConfig,
                        DuplicateNotificationConfig::isChooseColumns)) {
                    // Both using all columns
                    return Collections.emptyList();
                }
            }

            // Something has changed, but it could just be a change to the query that does not
            // alter the columns, so we need to derive the compiled columns and compare them
            // against the columns known to the dup store
            final FetchColumnNamesResponse response = duplicateCheckResourceProvider.get()
                    .fetchColumnNames(currentAnalytic.getUuid());
            if (response.isStoreInitialised()) {
                final List<String> currentColNames = response.getColumnNames();
                final List<String> newColNames = scheduledQueryAnalyticExecutor.extractColumnNames(analytic);
                LOGGER.debug("""
                        validateChanges() - {}
                        currentColNames: {},
                        newColNames:     {},
                        """, analytic, currentColNames, newColNames);

                if (!Objects.equals(currentColNames, newColNames)) {
                    return Message.warning(
                                    "The columns used to perform duplicate checks have changed. " +
                                    "If you save this Analytic Rule, all stored duplicate check data for this " +
                                    "analytic (as seen on the 'Duplicate Management' tab) " +
                                    "will be deleted on next execution.")
                            .asList();
                }
            } else {
                LOGGER.debug("validateChanges() - Store is not initialised");
            }
        }
        return Collections.emptyList();
    }

    private boolean requiresDupCheckStore(final AnalyticRuleDoc doc) {
        return doc != null
               && doc.getAnalyticProcessType() == AnalyticProcessType.SCHEDULED_QUERY
               && NullSafe.test(doc.getDuplicateNotificationConfig(), config ->
                config.isSuppressDuplicateNotifications() || config.isRememberNotifications());
    }

    // pkg private for testing
    Detection getExampleDetection() {
        final String stroom = "Test Environment";
        final String executionTime = "2024-02-29T17:48:40.396Z";
        final String detectTime = "2024-02-29T17:48:41.582Z";
        final String effectiveExecutionTime = "2024-02-29T16:00:00.000Z";

        // NOTE variables (including keys in maps) cannot use '-'
        return Detection.builder()
                .withDetectTime(detectTime)
                .withDetectorName("Example detector for test use.")
                .withHeadline("The headline for the detection.")
                .withDetailedDescription("A detailed description of what happened.")
                .withFullDescription("A full description of what happened.")
                .withDetectionRevision(123)
                .withDetectorVersion("v4.5.6")
                .withDetectorUuid("8909c01d-e1e7-4d22-b960-09933ddc4469")
                .withDetectionUniqueId("cc048f31-8a7f-41ae-a1d5-b80529de634d")
                .withDefunct(false)
                .withExecutionTime(executionTime)
                .withExecutionSchedule("1hr")
                .withEffectiveExecutionTime(effectiveExecutionTime)
                .addValue("name_1", "value_A")
                .addValue("name_2", "value_B")
                .addValue("name_3", "value_C")
                .addValue("name_4", null)
                .addLinkedEvents(new DetectionLinkedEvent(stroom, 1001L, 1L))
                .addLinkedEvents(new DetectionLinkedEvent(stroom, 1001L, 2L))
                .addLinkedEvents(new DetectionLinkedEvent(stroom, 2001L, 1L))
                .addLinkedEvents(new DetectionLinkedEvent(stroom, 2002L, 2L))
                .build();
    }
}

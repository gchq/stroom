/*
 * Copyright 2023 Crown Copyright
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

package stroom.query.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleResource;
import stroom.analytics.shared.NotificationConfig;
import stroom.analytics.shared.NotificationDestinationType;
import stroom.analytics.shared.NotificationStreamDestination;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.analytics.shared.ReportDoc;
import stroom.analytics.shared.ReportResource;
import stroom.analytics.shared.TableBuilderAnalyticProcessConfig;
import stroom.dashboard.client.main.UniqueUtil;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.entity.client.presenter.HasToolbar;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerResource;
import stroom.query.api.TimeRange;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QueryResource;
import stroom.svg.shared.SvgImage;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.AbstractAnalyticUiDefaultConfig;
import stroom.ui.config.shared.AnalyticUiDefaultConfig;
import stroom.ui.config.shared.ReportUiDefaultConfig;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;

/// A query document's Query tab: the query ([QueryEditController]), with buttons that make an
/// analytic rule or a report from it.
public class QueryDocEditController
        extends Controller
        implements HasToolbar {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);
    private static final AnalyticRuleResource ANALYTIC_RULE_RESOURCE = GWT.create(AnalyticRuleResource.class);
    private static final ReportResource REPORT_RESOURCE = GWT.create(ReportResource.class);
    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private final QueryEditController queryEditPresenter;
    private final RestFactory restFactory;
    private final UiConfigCache uiConfigCache;
    private final Toolbar toolbar;
    private DocRef docRef;

    /// Made by GIN, with its parts.
    ///
    /// @param eventBus           The application's event bus.
    /// @param queryEditPresenter The query.
    /// @param restFactory        Makes the rule or report.
    /// @param uiConfigCache      Gives a new rule's or report's defaults.
    @Inject
    public QueryDocEditController(final EventBus eventBus,
                                  final QueryEditController queryEditPresenter,
                                  final RestFactory restFactory,
                                  final UiConfigCache uiConfigCache) {
        super(eventBus);
        initWidget(queryEditPresenter.asWidget());
        own(queryEditPresenter);
        this.queryEditPresenter = queryEditPresenter;
        this.restFactory = restFactory;
        this.uiConfigCache = uiConfigCache;

        toolbar = new Toolbar("Query actions");
        toolbar.button(SvgImage.DOCUMENT_ANALYTIC_RULE, "Create Analytic Rule", this::createRule);
        toolbar.button(SvgImage.DOCUMENT_REPORT, "Create Report", this::createReport);

        queryEditPresenter.setOnChange(this::markChanged);
    }

    @Override
    public List<Widget> getDocumentButtons() {
        final List<Widget> list = new ArrayList<>();
        list.add(toolbar);
        list.addAll(queryEditPresenter.getDocumentButtons());
        return list;
    }

    @Override
    public List<Widget> getToolbars() {
        return queryEditPresenter.getToolbars();
    }

    private void createRule() {
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                final AnalyticUiDefaultConfig analyticUiDefaultConfig = uiConfig.getAnalyticUiDefaultConfig();
                final String query = queryEditPresenter.getQuery();
                final TimeRange timeRange = queryEditPresenter.getTimeRange();
                restFactory
                        .create(QUERY_RESOURCE)
                        .method(res -> res.validateQuery(query))
                        .onSuccess(validateExpressionResult -> {
                            if (!validateExpressionResult.isOk()) {
                                AlertEvent.fireError(this,
                                        validateExpressionResult.getString(),
                                        null);
                            } else {
                                AnalyticProcessType analyticProcessType = AnalyticProcessType.STREAMING;
                                if (validateExpressionResult.isGroupBy()) {
                                    analyticProcessType = AnalyticProcessType.SCHEDULED_QUERY;
                                }
                                createRule(analyticUiDefaultConfig, query, timeRange, analyticProcessType);
                            }
                        })
                        .onFailure(restError ->
                                AlertEvent.fireErrorFromException(this, restError.getException(), null))
                        .taskMonitorFactory(taskMonitor())
                        .exec();
            }
        }, taskMonitor());
    }

    private void createRule(final AnalyticUiDefaultConfig analyticUiDefaultConfig,
                            final String query,
                            final TimeRange timeRange,
                            final AnalyticProcessType analyticProcessType) {
        final Consumer<ExplorerNode> newDocumentConsumer = newNode -> {
            final DocRef ruleDoc = newNode.getDocRef();
            loadNewRule(ruleDoc, analyticUiDefaultConfig, query, timeRange, analyticProcessType);
        };

        // First get the explorer node for the docref.
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(res -> res.getFromDocRef(docRef))
                .onSuccess(explorerNode -> {
                    // Ask the user to create a new document.
                    ShowCreateDocumentDialogEvent.fire(
                            this,
                            "Create New Analytic Rule",
                            explorerNode,
                            AnalyticRuleDoc.TYPE,
                            docRef.getName(),
                            true,
                            newDocumentConsumer);
                })
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private void loadNewRule(final DocRef ruleDocRef,
                             final AnalyticUiDefaultConfig analyticUiDefaultConfig,
                             final String query,
                             final TimeRange timeRange,
                             final AnalyticProcessType analyticProcessType) {
        restFactory
                .create(ANALYTIC_RULE_RESOURCE)
                .method(res -> res.fetch(ruleDocRef.getUuid()))
                .onSuccess(doc -> {
                    // Create default config.
                    switch (analyticProcessType) {
                        case SCHEDULED_QUERY:
                            createDefaultScheduledRule(ruleDocRef, doc, analyticUiDefaultConfig, query, timeRange);
                            break;
                        case TABLE_BUILDER:
                            createDefaultTableBuilderRule(ruleDocRef, doc, analyticUiDefaultConfig, query, timeRange);
                            break;
                        default:
                            createDefaultStreamingRule(ruleDocRef, doc, analyticUiDefaultConfig, query, timeRange);
                    }
                })
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private void createDefaultStreamingRule(final DocRef ruleDocRef,
                                            final AnalyticRuleDoc doc,
                                            final AnalyticUiDefaultConfig analyticUiDefaultConfig,
                                            final String query,
                                            final TimeRange timeRange) {
        final AnalyticRuleDoc updated = doc
                .copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .timeRange(timeRange)
                .analyticProcessType(AnalyticProcessType.STREAMING)
                .notifications(createDefaultNotificationConfig(analyticUiDefaultConfig))
                .errorFeed(analyticUiDefaultConfig.getDefaultErrorFeed())
                .build();
        updateRule(ruleDocRef, updated);
    }

    private void createDefaultScheduledRule(final DocRef ruleDocRef,
                                            final AnalyticRuleDoc doc,
                                            final AnalyticUiDefaultConfig analyticUiDefaultConfig,
                                            final String query,
                                            final TimeRange timeRange) {
//        final SimpleDuration oneHour = SimpleDuration.builder().time(1).timeUnit(TimeUnit.HOURS).build();
//        final ScheduledQueryAnalyticProcessConfig analyticProcessConfig =
//                ScheduledQueryAnalyticProcessConfig.builder()
//                        .node(analyticUiDefaultConfig.getDefaultNode())
//                        .schedule(new Schedule(ScheduleType.CRON, CronExpressions.EVERY_HOUR.getExpression()))
//                        .contiguous(true)
//                        .scheduleBounds(new ScheduleBounds(System.currentTimeMillis(), null))
//                        .errorFeed(analyticUiDefaultConfig.getDefaultErrorFeed())
//                        .build();
        final AnalyticRuleDoc updated = doc
                .copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .timeRange(timeRange)
                .analyticProcessType(AnalyticProcessType.SCHEDULED_QUERY)
//                .analyticProcessConfig(analyticProcessConfig)
                .notifications(createDefaultNotificationConfig(analyticUiDefaultConfig))
                .errorFeed(analyticUiDefaultConfig.getDefaultErrorFeed())
                .build();
        updateRule(ruleDocRef, updated);
    }

    private void createDefaultTableBuilderRule(final DocRef ruleDocRef,
                                               final AnalyticRuleDoc doc,
                                               final AnalyticUiDefaultConfig analyticUiDefaultConfig,
                                               final String query,
                                               final TimeRange timeRange) {
        final SimpleDuration oneHour = SimpleDuration.builder().time(1).timeUnit(TimeUnit.HOURS).build();
        final TableBuilderAnalyticProcessConfig analyticProcessConfig =
                TableBuilderAnalyticProcessConfig.builder()
                        .node(analyticUiDefaultConfig.getDefaultNode())
                        .minMetaCreateTimeMs(System.currentTimeMillis())
                        .maxMetaCreateTimeMs(null)
                        .timeToWaitForData(oneHour)
                        .build();
        final AnalyticRuleDoc updated = doc
                .copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .timeRange(timeRange)
                .analyticProcessType(AnalyticProcessType.TABLE_BUILDER)
                .analyticProcessConfig(analyticProcessConfig)
                .notifications(createDefaultNotificationConfig(analyticUiDefaultConfig))
                .errorFeed(analyticUiDefaultConfig.getDefaultErrorFeed())
                .build();
        updateRule(ruleDocRef, updated);
    }

    private List<NotificationConfig> createDefaultNotificationConfig(
            final AbstractAnalyticUiDefaultConfig analyticUiDefaultConfig) {
        final NotificationStreamDestination destination =
                NotificationStreamDestination.builder()
                        .useSourceFeedIfPossible(false)
                        .destinationFeed(analyticUiDefaultConfig.getDefaultDestinationFeed())
                        .build();
        final NotificationConfig notificationConfig = NotificationConfig
                .builder()
                .uuid(UniqueUtil.generateUUID())
                .limitNotifications(false)
                .maxNotifications(100)
                .resumeAfter(SimpleDuration.builder().time(1).timeUnit(TimeUnit.HOURS).build())
                .destinationType(NotificationDestinationType.STREAM)
                .destination(destination)
                .build();
        final List<NotificationConfig> list = new ArrayList<>();
        list.add(notificationConfig);
        return list;
    }

    private void updateRule(final DocRef ruleDocRef,
                            final AnalyticRuleDoc ruleDoc) {
        restFactory
                .create(ANALYTIC_RULE_RESOURCE)
                .method(res -> res.update(ruleDocRef.getUuid(), ruleDoc))
                .onSuccess(doc -> OpenDocumentEvent.fire(
                        QueryDocEditController.this,
                        ruleDocRef,
                        true,
                        false))
                .taskMonitorFactory(taskMonitor())
                .exec();
    }


    private void createReport() {
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                final ReportUiDefaultConfig analyticUiDefaultConfig = uiConfig.getReportUiDefaultConfig();
                final String query = queryEditPresenter.getQuery();
                final TimeRange timeRange = queryEditPresenter.getTimeRange();
                restFactory
                        .create(QUERY_RESOURCE)
                        .method(res -> res.validateQuery(query))
                        .onSuccess(validateExpressionResult -> {
                            if (!validateExpressionResult.isOk()) {
                                AlertEvent.fireError(this,
                                        validateExpressionResult.getString(),
                                        null);
                            } else {
                                createReport(analyticUiDefaultConfig, query, timeRange,
                                        AnalyticProcessType.SCHEDULED_QUERY);
                            }
                        })
                        .onFailure(restError ->
                                AlertEvent.fireErrorFromException(this, restError.getException(), null))
                        .taskMonitorFactory(taskMonitor())
                        .exec();
            }
        }, taskMonitor());
    }

    private void createReport(final ReportUiDefaultConfig analyticUiDefaultConfig,
                              final String query,
                              final TimeRange timeRange,
                              final AnalyticProcessType analyticProcessType) {
        final Consumer<ExplorerNode> newDocumentConsumer = newNode -> {
            final DocRef ruleDoc = newNode.getDocRef();
            loadNewReport(ruleDoc, analyticUiDefaultConfig, query, timeRange, analyticProcessType);
        };

        // First get the explorer node for the docref.
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(res -> res.getFromDocRef(docRef))
                .onSuccess(explorerNode -> {
                    // Ask the user to create a new document.
                    ShowCreateDocumentDialogEvent.fire(
                            this,
                            "Create New Report",
                            explorerNode,
                            ReportDoc.TYPE,
                            docRef.getName(),
                            true,
                            newDocumentConsumer);
                })
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private void loadNewReport(final DocRef ruleDocRef,
                               final ReportUiDefaultConfig analyticUiDefaultConfig,
                               final String query,
                               final TimeRange timeRange,
                               final AnalyticProcessType analyticProcessType) {
        restFactory
                .create(REPORT_RESOURCE)
                .method(res -> res.fetch(ruleDocRef.getUuid()))
                .onSuccess(doc -> {
                    // Create default config.
                    switch (analyticProcessType) {
                        case SCHEDULED_QUERY:
                            createDefaultScheduledReport(ruleDocRef, doc, analyticUiDefaultConfig, query, timeRange);
                            break;
//                        case TABLE_BUILDER:
//                            createDefaultTableBuilderRule(ruleDocRef, doc, analyticUiDefaultConfig, query, timeRange);
//                            break;
//                        default:
//                            createDefaultStreamingRule(ruleDocRef, doc, analyticUiDefaultConfig, query, timeRange);
                    }
                })
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private void createDefaultStreamingReport(final DocRef ruleDocRef,
                                              final ReportDoc doc,
                                              final AnalyticUiDefaultConfig analyticUiDefaultConfig,
                                              final String query,
                                              final TimeRange timeRange) {
        final ReportDoc updated = doc
                .copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .timeRange(timeRange)
                .analyticProcessType(AnalyticProcessType.STREAMING)
                .notifications(createDefaultNotificationConfig(analyticUiDefaultConfig))
                .errorFeed(analyticUiDefaultConfig.getDefaultErrorFeed())
                .build();
        updateReport(ruleDocRef, updated);
    }

    private void createDefaultScheduledReport(final DocRef ruleDocRef,
                                              final ReportDoc doc,
                                              final ReportUiDefaultConfig analyticUiDefaultConfig,
                                              final String query,
                                              final TimeRange timeRange) {
//        final SimpleDuration oneHour = SimpleDuration.builder().time(1).timeUnit(TimeUnit.HOURS).build();
//        final ScheduledQueryAnalyticProcessConfig analyticProcessConfig =
//                ScheduledQueryAnalyticProcessConfig.builder()
//                        .node(analyticUiDefaultConfig.getDefaultNode())
//                        .schedule(new Schedule(ScheduleType.CRON, CronExpressions.EVERY_HOUR.getExpression()))
//                        .contiguous(true)
//                        .scheduleBounds(new ScheduleBounds(System.currentTimeMillis(), null))
//                        .errorFeed(analyticUiDefaultConfig.getDefaultErrorFeed())
//                        .build();
        final ReportDoc updated = doc
                .copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .timeRange(timeRange)
                .analyticProcessType(AnalyticProcessType.SCHEDULED_QUERY)
//                .analyticProcessConfig(analyticProcessConfig)
                .notifications(createDefaultNotificationConfig(analyticUiDefaultConfig))
                .errorFeed(analyticUiDefaultConfig.getDefaultErrorFeed())
                .build();
        updateReport(ruleDocRef, updated);
    }

    private void createDefaultTableBuilderReport(final DocRef ruleDocRef,
                                                 final ReportDoc doc,
                                                 final AnalyticUiDefaultConfig analyticUiDefaultConfig,
                                                 final String query,
                                                 final TimeRange timeRange) {
        final SimpleDuration oneHour = SimpleDuration.builder().time(1).timeUnit(TimeUnit.HOURS).build();
        final TableBuilderAnalyticProcessConfig analyticProcessConfig =
                TableBuilderAnalyticProcessConfig.builder()
                        .node(analyticUiDefaultConfig.getDefaultNode())
                        .minMetaCreateTimeMs(System.currentTimeMillis())
                        .maxMetaCreateTimeMs(null)
                        .timeToWaitForData(oneHour)
                        .build();
        final ReportDoc updated = doc
                .copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query(query)
                .timeRange(timeRange)
                .analyticProcessType(AnalyticProcessType.TABLE_BUILDER)
                .analyticProcessConfig(analyticProcessConfig)
                .notifications(createDefaultNotificationConfig(analyticUiDefaultConfig))
                .errorFeed(analyticUiDefaultConfig.getDefaultErrorFeed())
                .build();
        updateReport(ruleDocRef, updated);
    }

    private void updateReport(final DocRef ruleDocRef,
                              final ReportDoc ruleDoc) {
        restFactory
                .create(REPORT_RESOURCE)
                .method(res -> res.update(ruleDocRef.getUuid(), ruleDoc))
                .onSuccess(doc -> OpenDocumentEvent.fire(
                        QueryDocEditController.this,
                        ruleDocRef,
                        true,
                        false))
                .taskMonitorFactory(taskMonitor())
                .exec();
    }


    private TaskMonitorFactory taskMonitor() {
        final TaskMonitorFactory provided = getProvidedTaskMonitor();
        return provided != null ? provided : new DefaultTaskMonitorFactory(this);
    }

    /// Shows a query document's query, time range and table preferences.
    ///
    /// @param docRef The document.
    /// @param entity The document.
    public void read(final DocRef docRef, final QueryDoc entity) {
        this.docRef = docRef;
        queryEditPresenter.setTimeRange(entity.getTimeRange());
        queryEditPresenter.setQuery(docRef, entity.getQuery());
        queryEditPresenter.read(entity.getQueryTablePreferences());
    }

    /// @param visible Whether the query's tab is showing.
    public void onContentTabVisible(final boolean visible) {
        queryEditPresenter.onContentTabVisible(visible);
    }

    /// @param entity The document.
    /// @return A copy of it with the query, time range and table preferences shown.
    public QueryDoc write(final QueryDoc entity) {
        return entity
                .copy()
                .timeRange(queryEditPresenter.getTimeRange())
                .query(queryEditPresenter.getQuery())
                .queryTablePreferences(queryEditPresenter.write())
                .build();
    }

    /// Runs the query again.
    public void start() {
        queryEditPresenter.start();
    }

    /// Stops the query.
    public void stop() {
        queryEditPresenter.stop();
    }
}

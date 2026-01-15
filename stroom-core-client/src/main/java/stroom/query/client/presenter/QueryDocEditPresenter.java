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

package stroom.query.client.presenter;

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
import stroom.analytics.shared.ReportSettings;
import stroom.analytics.shared.TableBuilderAnalyticProcessConfig;
import stroom.dashboard.client.main.UniqueUtil;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.client.presenter.HasToolbar;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerResource;
import stroom.query.api.TimeRange;
import stroom.query.client.presenter.QueryEditPresenter.QueryEditView;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QueryResource;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.AbstractAnalyticUiDefaultConfig;
import stroom.ui.config.shared.AnalyticUiDefaultConfig;
import stroom.ui.config.shared.ReportUiDefaultConfig;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.InlineSvgButton;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;

public class QueryDocEditPresenter
        extends DocumentEditPresenter<QueryEditView, QueryDoc>
        implements HasToolbar {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);
    private static final AnalyticRuleResource ANALYTIC_RULE_RESOURCE = GWT.create(AnalyticRuleResource.class);
    private static final ReportResource REPORT_RESOURCE = GWT.create(ReportResource.class);
    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private final QueryEditPresenter queryEditPresenter;
    private final RestFactory restFactory;
    private final UiConfigCache uiConfigCache;
    private final InlineSvgButton createRuleButton;
    private final InlineSvgButton createReportButton;
    private final ButtonPanel toolbar;
    private DocRef docRef;

    @Inject
    public QueryDocEditPresenter(final EventBus eventBus,
                                 final QueryEditPresenter queryEditPresenter,
                                 final RestFactory restFactory,
                                 final UiConfigCache uiConfigCache) {
        super(eventBus, queryEditPresenter.getView());
        this.queryEditPresenter = queryEditPresenter;
        this.restFactory = restFactory;
        this.uiConfigCache = uiConfigCache;

        createRuleButton = new InlineSvgButton();
        createRuleButton.setSvg(SvgImage.DOCUMENT_ANALYTIC_RULE);
        createRuleButton.setTitle("Create Rule");
        createRuleButton.setVisible(true);

        createReportButton = new InlineSvgButton();
        createReportButton.setSvg(SvgImage.DOCUMENT_REPORT);
        createReportButton.setTitle("Create Report");
        createReportButton.setVisible(true);

        toolbar = new ButtonPanel();
        toolbar.addButton(createRuleButton);
        toolbar.addButton(createReportButton);
    }

    @Override
    public List<Widget> getToolbars() {
        final List<Widget> list = new ArrayList<>();
        list.add(toolbar);
        list.addAll(queryEditPresenter.getToolbars());
        return list;
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(queryEditPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
        registerHandler(createRuleButton.addClickHandler(event -> createRule()));
        registerHandler(createReportButton.addClickHandler(event -> createReport()));
    }

    private void createRule() {
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                final AnalyticUiDefaultConfig analyticUiDefaultConfig = uiConfig.getAnalyticUiDefaultConfig();
                if (analyticUiDefaultConfig.getDefaultErrorFeed() == null) {
                    AlertEvent.fireError(this, "No default error feed configured", null);
                } else if (analyticUiDefaultConfig.getDefaultDestinationFeed() == null) {
                    AlertEvent.fireError(this, "No default destination feed configured", null);
                } else if (analyticUiDefaultConfig.getDefaultNode() == null) {
                    AlertEvent.fireError(this, "No default processing node configured", null);
                } else {
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
                            .onFailure(restError -> {
                                AlertEvent.fireErrorFromException(this, restError.getException(), null);
                            })
                            .taskMonitorFactory(this)
                            .exec();
                }
            }
        }, this);
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
                .taskMonitorFactory(this)
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
                .taskMonitorFactory(this)
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
                        QueryDocEditPresenter.this,
                        ruleDocRef,
                        true,
                        false))
                .taskMonitorFactory(this)
                .exec();
    }


    private void createReport() {
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                final ReportUiDefaultConfig analyticUiDefaultConfig = uiConfig.getReportUiDefaultConfig();
                if (analyticUiDefaultConfig.getDefaultErrorFeed() == null) {
                    AlertEvent.fireError(this, "No default error feed configured", null);
                } else if (analyticUiDefaultConfig.getDefaultDestinationFeed() == null) {
                    AlertEvent.fireError(this, "No default destination feed configured", null);
                } else if (analyticUiDefaultConfig.getDefaultNode() == null) {
                    AlertEvent.fireError(this, "No default processing node configured", null);
                } else {
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
                            .onFailure(restError -> {
                                AlertEvent.fireErrorFromException(this, restError.getException(), null);
                            })
                            .taskMonitorFactory(this)
                            .exec();
                }
            }
        }, this);
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
                .taskMonitorFactory(this)
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
                .taskMonitorFactory(this)
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
                .reportSettings(ReportSettings.builder().fileType(DownloadSearchResultFileType.EXCEL).build())
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
                .reportSettings(ReportSettings.builder().fileType(DownloadSearchResultFileType.EXCEL).build())
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
                .reportSettings(ReportSettings.builder().fileType(DownloadSearchResultFileType.EXCEL).build())
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
                        QueryDocEditPresenter.this,
                        ruleDocRef,
                        true,
                        false))
                .taskMonitorFactory(this)
                .exec();
    }


    @Override
    public void onRead(final DocRef docRef, final QueryDoc entity, final boolean readOnly) {
        this.docRef = docRef;
        queryEditPresenter.setTimeRange(entity.getTimeRange());
        queryEditPresenter.setQuery(docRef, entity.getQuery(), readOnly);
        queryEditPresenter.read(entity.getQueryTablePreferences());
    }

    public void onContentTabVisible(final boolean visible) {
        queryEditPresenter.onContentTabVisible(visible);
    }

    @Override
    protected QueryDoc onWrite(final QueryDoc entity) {
        entity.setTimeRange(queryEditPresenter.getTimeRange());
        entity.setQuery(queryEditPresenter.getQuery());
        entity.setQueryTablePreferences(queryEditPresenter.write());
        return entity;
    }

    @Override
    public void onClose() {
        queryEditPresenter.onClose();
        super.onClose();
    }

    void start() {
        queryEditPresenter.start();
    }

    void stop() {
        queryEditPresenter.stop();
    }

    @Override
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        super.setTaskMonitorFactory(taskMonitorFactory);
        queryEditPresenter.setTaskMonitorFactory(taskMonitorFactory);
    }
}

/*
 * Copyright 2024 Crown Copyright
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

package stroom.analytics.client.presenter;

import stroom.analytics.client.presenter.AnalyticProcessingPresenter.AnalyticProcessingView;
import stroom.analytics.shared.AnalyticProcessConfig;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.analytics.shared.TableBuilderAnalyticProcessConfig;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.client.event.ChangeDataEvent;
import stroom.pipeline.client.event.ChangeDataEvent.ChangeDataHandler;
import stroom.pipeline.client.event.HasChangeDataHandlers;
import stroom.security.shared.DocumentPermissionNames;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.GwtNullSafe;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class AnalyticProcessingPresenter
        extends DocumentEditPresenter<AnalyticProcessingView, AnalyticRuleDoc>
        implements AnalyticProcessingUiHandlers, HasChangeDataHandlers<AnalyticProcessType> {

    private final DocSelectionBoxPresenter errorFeedPresenter;
    private final ScheduledProcessingPresenter scheduledProcessingPresenter;
    private final TableBuilderProcessingPresenter tableBuilderProcessingPresenter;
    private final StreamingProcessingPresenter streamingProcessingPresenter;
    private final UiConfigCache uiConfigCache;

    @Inject
    public AnalyticProcessingPresenter(final EventBus eventBus,
                                       final AnalyticProcessingView view,
                                       final DocSelectionBoxPresenter errorFeedPresenter,
                                       final ScheduledProcessingPresenter scheduledProcessingPresenter,
                                       final TableBuilderProcessingPresenter tableBuilderProcessingPresenter,
                                       final StreamingProcessingPresenter streamingProcessingPresenter,
                                       final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.uiConfigCache = uiConfigCache;
        this.errorFeedPresenter = errorFeedPresenter;
        this.scheduledProcessingPresenter = scheduledProcessingPresenter;
        this.tableBuilderProcessingPresenter = tableBuilderProcessingPresenter;
        this.streamingProcessingPresenter = streamingProcessingPresenter;
        view.setUiHandlers(this);

        errorFeedPresenter.setIncludedTypes(FeedDoc.DOCUMENT_TYPE);
        errorFeedPresenter.setRequiredPermissions(DocumentPermissionNames.READ);
        getView().setErrorFeedView(errorFeedPresenter.getView());
    }

    public void setDocumentEditPresenter(final DocumentEditPresenter<?, ?> documentEditPresenter) {
        scheduledProcessingPresenter.setDocumentEditPresenter(documentEditPresenter);
        streamingProcessingPresenter.setDocumentEditPresenter(documentEditPresenter);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(errorFeedPresenter.addDataSelectionHandler(e -> onDirty()));
        registerHandler(tableBuilderProcessingPresenter.addDirtyHandler(event -> setDirty(true)));
        registerHandler(streamingProcessingPresenter.addDirtyHandler(event -> setDirty(true)));
    }

    @Override
    public void onProcessingTypeChange() {
        setDirty(true);
        setProcessType(getView().getProcessingType());
        ChangeDataEvent.fire(this, getView().getProcessingType());
    }

    @Override
    public HandlerRegistration addChangeDataHandler(final ChangeDataHandler<AnalyticProcessType> handler) {
        return addHandlerToSource(ChangeDataEvent.getType(), handler);
    }

    @Override
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc analyticRuleDoc, final boolean readOnly) {
        uiConfigCache.get(extendedUiConfig -> {
            if (extendedUiConfig != null) {
                final DocRef selectedDocRef = GwtNullSafe.requireNonNullElseGet(
                        analyticRuleDoc.getErrorFeed(),
                        () -> extendedUiConfig.getAnalyticUiDefaultConfig().getDefaultErrorFeed());

                errorFeedPresenter.setSelectedEntityReference(selectedDocRef, true);

                final AnalyticProcessConfig analyticProcessConfig = analyticRuleDoc.getAnalyticProcessConfig();
                final AnalyticProcessType analyticProcessType = analyticRuleDoc.getAnalyticProcessType() == null
                        ? AnalyticProcessType.SCHEDULED_QUERY
                        : analyticRuleDoc.getAnalyticProcessType();
                setProcessType(analyticProcessType);

                if (AnalyticProcessType.SCHEDULED_QUERY.equals(analyticProcessType)) {
                    scheduledProcessingPresenter
                            .read(docRef);
                } else if (AnalyticProcessType.STREAMING.equals(analyticProcessType)) {
                    streamingProcessingPresenter
                            .update(getEntity(), isReadOnly(), analyticRuleDoc.getQuery());
                } else if (analyticProcessConfig instanceof TableBuilderAnalyticProcessConfig) {
                    //noinspection PatternVariableCanBeUsed // Not in GWT
                    final TableBuilderAnalyticProcessConfig ac =
                            (TableBuilderAnalyticProcessConfig) analyticProcessConfig;
                    tableBuilderProcessingPresenter.read(docRef, ac);

                }
            }
        }, this);
    }

    private void setProcessType(final AnalyticProcessType analyticProcessType) {
        switch (analyticProcessType) {
            case STREAMING: {
                streamingProcessingPresenter.update(getEntity(), isReadOnly(), getEntity().getQuery());
                getView().setProcessSettings(streamingProcessingPresenter.getView());
                break;
            }
            case SCHEDULED_QUERY: {
                scheduledProcessingPresenter.read(getEntity().asDocRef());
                getView().setProcessSettings(scheduledProcessingPresenter.getView());
                break;
            }
            case TABLE_BUILDER: {
                getView().setProcessSettings(tableBuilderProcessingPresenter.getView());
                break;
            }
        }

        getView().setProcessingType(analyticProcessType);
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc analyticRuleDoc) {
        AnalyticProcessConfig analyticProcessConfig = null;
        switch (getView().getProcessingType()) {
            case STREAMING:
                break;
            case TABLE_BUILDER:
                analyticProcessConfig = tableBuilderProcessingPresenter.write();
                break;
            case SCHEDULED_QUERY:
                break;
        }

        return analyticRuleDoc
                .copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .errorFeed(errorFeedPresenter.getSelectedEntityReference())
                .analyticProcessType(getView().getProcessingType())
                .analyticProcessConfig(analyticProcessConfig)
                .build();
    }

    @Override
    public void onDirty() {
        setDirty(true);
    }

    @Override
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        super.setTaskMonitorFactory(taskMonitorFactory);
        this.scheduledProcessingPresenter.setTaskMonitorFactory(taskMonitorFactory);
        this.tableBuilderProcessingPresenter.setTaskMonitorFactory(taskMonitorFactory);
        this.streamingProcessingPresenter.setTaskMonitorFactory(taskMonitorFactory);
    }


    // --------------------------------------------------------------------------------


    public interface AnalyticProcessingView extends View, HasUiHandlers<AnalyticProcessingUiHandlers> {

        void setErrorFeedView(View view);

        AnalyticProcessType getProcessingType();

        void setProcessingType(AnalyticProcessType analyticProcessType);

        void setProcessSettings(View view);
    }
}

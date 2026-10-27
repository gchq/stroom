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

package stroom.analytics.client.presenter;

import stroom.analytics.client.component.ScheduledProcessingController;
import stroom.analytics.client.component.TableBuilderProcessingController;
import stroom.analytics.client.presenter.AbstractProcessingPresenter.AnalyticProcessingView;
import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.AnalyticProcessConfig;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.TableBuilderAnalyticProcessConfig;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.component.client.Contexts;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.View;

import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

public abstract class AbstractProcessingPresenter<D extends AbstractAnalyticRuleDoc>
        extends DocPresenter<AnalyticProcessingView, D>
        implements AnalyticProcessingUiHandlers {

    private final ScheduledProcessingController scheduledProcessing;
    private final TableBuilderProcessingController tableBuilderProcessing;
    private final StreamingProcessingPresenter streamingProcessingPresenter;
    private final UiConfigCache uiConfigCache;

    private Consumer<AnalyticProcessType> onProcessingTypeChange = processingType -> {
    };

    @Inject
    public AbstractProcessingPresenter(final EventBus eventBus,
                                       final AnalyticProcessingView view,
                                       final ScheduledProcessingController scheduledProcessing,
                                       final TableBuilderProcessingController tableBuilderProcessing,
                                       final StreamingProcessingPresenter streamingProcessingPresenter,
                                       final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        // Read only as its document is
        watch(Contexts.READ_ONLY, getView()::onReadOnly);
        own(scheduledProcessing);
        own(tableBuilderProcessing);
        own(streamingProcessingPresenter);
        this.uiConfigCache = uiConfigCache;
        this.scheduledProcessing = scheduledProcessing;
        this.tableBuilderProcessing = tableBuilderProcessing;
        this.streamingProcessingPresenter = streamingProcessingPresenter;
        view.setUiHandlers(this);

        tableBuilderProcessing.setOnChange(this::onChange);
    }

    public void setDocumentEditPresenter(final DocPresenter<?, ?> documentEditPresenter) {
        scheduledProcessing.setIsDocumentDirty(documentEditPresenter::isDirty);
        streamingProcessingPresenter.setDocumentEditPresenter(documentEditPresenter);
    }

    @Override
    public void onProcessingTypeChange() {
        onChange();
        setProcessType(getView().getProcessingType());
        onProcessingTypeChange.accept(getView().getProcessingType());
    }

    /// @param onProcessingTypeChange What to run with the processing type when the user changes it.
    public void setOnProcessingTypeChange(final Consumer<AnalyticProcessType> onProcessingTypeChange) {
        this.onProcessingTypeChange = onProcessingTypeChange;
    }

    @Override
    protected void onRead(final DocRef docRef, final D analyticRuleDoc) {
        uiConfigCache.get(extendedUiConfig -> {
            if (extendedUiConfig != null) {
                final AnalyticProcessConfig analyticProcessConfig = analyticRuleDoc.getAnalyticProcessConfig();
                final AnalyticProcessType analyticProcessType = Objects.requireNonNullElse(
                        analyticRuleDoc.getAnalyticProcessType(),
                        AnalyticProcessType.SCHEDULED_QUERY);
                setProcessType(analyticProcessType);

                if (AnalyticProcessType.SCHEDULED_QUERY.equals(analyticProcessType)) {
                    scheduledProcessing
                            .read(docRef);
                } else if (AnalyticProcessType.STREAMING.equals(analyticProcessType)) {
                    streamingProcessingPresenter
                            .update(getEntity(), analyticRuleDoc.getQuery());
                } else if (analyticProcessConfig instanceof TableBuilderAnalyticProcessConfig) {
                    //noinspection PatternVariableCanBeUsed // Not in GWT
                    final TableBuilderAnalyticProcessConfig ac =
                            (TableBuilderAnalyticProcessConfig) analyticProcessConfig;
                    tableBuilderProcessing.read(docRef, ac);

                }
            }
        }, this);
    }

    private void setProcessType(final AnalyticProcessType analyticProcessType) {
        switch (analyticProcessType) {
            case STREAMING: {
                streamingProcessingPresenter.update(getEntity(), getEntity().getQuery());
                getView().setProcessSettings(streamingProcessingPresenter.getView());
                break;
            }
            case SCHEDULED_QUERY: {
                scheduledProcessing.read(getEntity().asDocRef());
                getView().setProcessSettings(scheduledProcessing);
                break;
            }
            case TABLE_BUILDER: {
                getView().setProcessSettings(tableBuilderProcessing);
                break;
            }
        }

        getView().setProcessingType(analyticProcessType);
    }

    protected AnalyticProcessConfig writeProcessConfig() {
        AnalyticProcessConfig analyticProcessConfig = null;
        switch (getView().getProcessingType()) {
            case STREAMING:
                break;
            case TABLE_BUILDER:
                analyticProcessConfig = tableBuilderProcessing.write();
                break;
            case SCHEDULED_QUERY:
                break;
        }
        return analyticProcessConfig;
    }


    // --------------------------------------------------------------------------------


    public interface AnalyticProcessingView extends View, HasUiHandlers<AnalyticProcessingUiHandlers> {

        /// Shows whether the user may change what this shows.
        ///
        /// @param readOnly Whether it may not be changed.
        void onReadOnly(boolean readOnly);

        void addProcessingType(AnalyticProcessType processingType);

        AnalyticProcessType getProcessingType();

        void setProcessingType(AnalyticProcessType analyticProcessType);

        void setProcessSettings(IsWidget settings);
    }
}

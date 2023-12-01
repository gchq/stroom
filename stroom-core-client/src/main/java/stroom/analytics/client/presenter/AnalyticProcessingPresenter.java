/*
 * Copyright 2022 Crown Copyright
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
 *
 */

package stroom.analytics.client.presenter;

import stroom.analytics.client.presenter.AnalyticProcessingPresenter.AnalyticProcessingView;
import stroom.analytics.shared.AnalyticProcessConfig;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.analytics.shared.ScheduledQueryAnalyticProcessConfig;
import stroom.analytics.shared.StreamingAnalyticProcessConfig;
import stroom.analytics.shared.TableBuilderAnalyticProcessConfig;
import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.pipeline.client.event.ChangeDataEvent;
import stroom.pipeline.client.event.ChangeDataEvent.ChangeDataHandler;
import stroom.pipeline.client.event.HasChangeDataHandlers;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

public class AnalyticProcessingPresenter
        extends DocumentEditPresenter<AnalyticProcessingView, AnalyticRuleDoc>
        implements AnalyticProcessingUiHandlers, HasChangeDataHandlers<AnalyticProcessType> {

    private final EditorPresenter editorPresenter;
    private final ScheduledQueryProcessingPresenter scheduledQueryProcessingPresenter;
    private final TableBuilderProcessingPresenter tableBuilderProcessingPresenter;
    private final StreamingProcessingPresenter streamingProcessingPresenter;

    @Inject
    public AnalyticProcessingPresenter(final EventBus eventBus,
                                       final AnalyticProcessingView view,
                                       final EditorPresenter editorPresenter,
                                       final ScheduledQueryProcessingPresenter scheduledQueryProcessingPresenter,
                                       final TableBuilderProcessingPresenter tableBuilderProcessingPresenter,
                                       final StreamingProcessingPresenter streamingProcessingPresenter) {
        super(eventBus, view);
        this.editorPresenter = editorPresenter;
        this.scheduledQueryProcessingPresenter = scheduledQueryProcessingPresenter;
        this.tableBuilderProcessingPresenter = tableBuilderProcessingPresenter;
        this.streamingProcessingPresenter = streamingProcessingPresenter;
        view.setUiHandlers(this);

        view.setQueryEditorView(editorPresenter.getView());
        this.editorPresenter.setMode(AceEditorMode.STROOM_QUERY);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(editorPresenter.addValueChangeHandler(event -> setDirty(true)));
        registerHandler(scheduledQueryProcessingPresenter.addDirtyHandler(event -> setDirty(true)));
        registerHandler(tableBuilderProcessingPresenter.addDirtyHandler(event -> setDirty(true)));
        registerHandler(streamingProcessingPresenter.addDirtyHandler(event -> setDirty(true)));
    }

    @Override
    public void onProcessingTypeChange() {
        setProcessType(getView().getProcessingType(), getEntity().getAnalyticProcessConfig());
        ChangeDataEvent.fire(this, getView().getProcessingType());
    }

    @Override
    public HandlerRegistration addChangeDataHandler(final ChangeDataHandler<AnalyticProcessType> handler) {
        return addHandlerToSource(ChangeDataEvent.getType(), handler);
    }

    @Override
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc analyticRuleDoc, final boolean readOnly) {
        final AnalyticProcessConfig analyticProcessConfig = analyticRuleDoc.getAnalyticProcessConfig();
        if (analyticProcessConfig != null) {
            final AnalyticProcessType analyticProcessType = analyticRuleDoc.getAnalyticProcessType() == null
                    ? AnalyticProcessType.SCHEDULED_QUERY
                    : analyticRuleDoc.getAnalyticProcessType();
            setProcessType(analyticProcessType, analyticProcessConfig);

            editorPresenter.setText(analyticRuleDoc.getQuery());

            if (analyticProcessConfig instanceof TableBuilderAnalyticProcessConfig) {
                final TableBuilderAnalyticProcessConfig ac =
                        (TableBuilderAnalyticProcessConfig) analyticProcessConfig;
                tableBuilderProcessingPresenter.read(docRef, ac);

            } else if (analyticProcessConfig instanceof ScheduledQueryAnalyticProcessConfig) {
                final ScheduledQueryAnalyticProcessConfig ac =
                        (ScheduledQueryAnalyticProcessConfig) analyticProcessConfig;
                scheduledQueryProcessingPresenter.read(docRef, ac);

            } else if (analyticProcessConfig instanceof StreamingAnalyticProcessConfig) {
                final StreamingAnalyticProcessConfig ac =
                        (StreamingAnalyticProcessConfig) analyticProcessConfig;
                streamingProcessingPresenter.read(ac);
                streamingProcessingPresenter.update(getEntity(), isReadOnly(), editorPresenter.getText());
            }
        }
    }

    private void setProcessType(final AnalyticProcessType analyticProcessType,
                                final AnalyticProcessConfig analyticProcessConfig) {
        if (analyticProcessConfig != null) {
            switch (analyticProcessType) {
                case STREAMING: {
                    streamingProcessingPresenter.update(getEntity(), isReadOnly(), editorPresenter.getText());
                    getView().setProcessSettings(streamingProcessingPresenter.getView());
                    break;
                }
                case SCHEDULED_QUERY: {
                    getView().setProcessSettings(scheduledQueryProcessingPresenter.getView());
                    break;
                }
                case TABLE_BUILDER: {
                    getView().setProcessSettings(tableBuilderProcessingPresenter.getView());
                    break;
                }
            }
        }

        getView().setProcessingType(analyticProcessType);
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc analyticRuleDoc) {
        AnalyticProcessConfig analyticProcessConfig = null;
        switch (getView().getProcessingType()) {
            case STREAMING:
                analyticProcessConfig = streamingProcessingPresenter.write();
                break;
            case TABLE_BUILDER:
                analyticProcessConfig = tableBuilderProcessingPresenter.write();
                break;
            case SCHEDULED_QUERY:
                analyticProcessConfig = scheduledQueryProcessingPresenter.write();
                break;
        }

        return analyticRuleDoc.copy()
                .query(editorPresenter.getText())
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .analyticProcessType(getView().getProcessingType())
                .analyticProcessConfig(analyticProcessConfig)
                .build();
    }

    @Override
    public void onDirty() {
        setDirty(true);
    }

    public interface AnalyticProcessingView extends View, HasUiHandlers<AnalyticProcessingUiHandlers> {

        void setQueryEditorView(View view);

        AnalyticProcessType getProcessingType();

        void setProcessingType(AnalyticProcessType analyticProcessType);

        void setProcessSettings(View view);
    }
}

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

package stroom.search.elastic.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.client.presenter.DynamicFieldSelectionListModel;
import stroom.search.elastic.client.presenter.ElasticIndexSettingsPresenter.ElasticIndexSettingsView;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexResource;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.UUID;

public class ElasticIndexSettingsPresenter extends DocumentEditPresenter<ElasticIndexSettingsView, ElasticIndexDoc>
        implements ElasticIndexSettingsUiHandlers {

    private static final ElasticIndexResource ELASTIC_INDEX_RESOURCE = GWT.create(ElasticIndexResource.class);

    private final DocSelectionBoxPresenter clusterPresenter;
    private final DocSelectionBoxPresenter vectorGenerationModelPresenter;
    private final DocSelectionBoxPresenter rerankModelPresenter;
    private final DocSelectionBoxPresenter pipelinePresenter;
    private final RestFactory restFactory;
    private final DynamicFieldSelectionListModel fieldSelectionBoxModel;

    @Inject
    public ElasticIndexSettingsPresenter(
            final EventBus eventBus,
            final ElasticIndexSettingsView view,
            final DocSelectionBoxPresenter clusterPresenter,
            final DocSelectionBoxPresenter vectorGenerationModelPresenter,
            final DocSelectionBoxPresenter rerankModelPresenter,
            final DocSelectionBoxPresenter pipelinePresenter,
            final RestFactory restFactory,
            final DynamicFieldSelectionListModel fieldSelectionBoxModel) {
        super(eventBus, view);

        this.clusterPresenter = clusterPresenter;
        this.vectorGenerationModelPresenter = vectorGenerationModelPresenter;
        this.rerankModelPresenter = rerankModelPresenter;
        this.pipelinePresenter = pipelinePresenter;
        this.restFactory = restFactory;
        this.fieldSelectionBoxModel = fieldSelectionBoxModel;

        clusterPresenter.setIncludedTypes(ElasticClusterDoc.TYPE);
        clusterPresenter.setRequiredPermissions(DocumentPermission.USE);
        vectorGenerationModelPresenter.setIncludedTypes(OpenAIModelDoc.TYPE);
        vectorGenerationModelPresenter.setRequiredPermissions(DocumentPermission.USE);
        rerankModelPresenter.setIncludedTypes(OpenAIModelDoc.TYPE);
        rerankModelPresenter.setRequiredPermissions(DocumentPermission.USE);
        pipelinePresenter.setIncludedTypes(PipelineDoc.TYPE);
        pipelinePresenter.setRequiredPermissions(DocumentPermission.VIEW);

        view.setUiHandlers(this);
        view.setDefaultExtractionPipelineView(pipelinePresenter.getView());
        view.setClusterView(clusterPresenter.getView());
        view.setVectorGenerationModelView(vectorGenerationModelPresenter.getView());
        view.setRerankModelView(rerankModelPresenter.getView());
    }

    @Override
    protected void onBind() {
        // If the selected `ElasticCluster` changes, set the dirty flag to `true`
        registerHandler(clusterPresenter.addDataSelectionHandler(event -> setDirty(true)));
        registerHandler(vectorGenerationModelPresenter.addDataSelectionHandler(event -> setDirty(true)));
        registerHandler(rerankModelPresenter.addDataSelectionHandler(event -> setDirty(true)));
        registerHandler(pipelinePresenter.addDataSelectionHandler(selection -> setDirty(true)));
    }

    @Override
    public void onChange() {
        setDirty(true);
    }

    @Override
    public void onTestIndex() {
        final ElasticIndexDoc indexDoc = ElasticIndexDoc
                .builder()
                .uuid(UUID.randomUUID().toString())
                .build();
        final ElasticIndexDoc index = onWrite(indexDoc);
        restFactory
                .create(ELASTIC_INDEX_RESOURCE)
                .method(res -> res.testIndex(index))
                .onSuccess(result -> {
                    if (result.isOk()) {
                        AlertEvent.fireInfo(this, "Connection Success", result.getMessage(), null);
                    } else {
                        AlertEvent.fireError(this, "Connection Failure", result.getMessage(), null);
                    }
                })
                .taskMonitorFactory(this)
                .exec();
    }

    @Override
    protected void onRead(final DocRef docRef, final ElasticIndexDoc index, final boolean readOnly) {
        clusterPresenter.setSelectedEntityReference(index.getClusterRef(), true);
        vectorGenerationModelPresenter.setSelectedEntityReference(index.getVectorGenerationModelRef(), true);
        rerankModelPresenter.setSelectedEntityReference(index.getRerankModelRef(), true);
        getView().setIndexName(index.getIndexName());
        getView().setSearchSlices(index.getSearchSlices());
        getView().setSearchScrollSize(index.getSearchScrollSize());
        getView().setTimeField(index.getTimeField());
        getView().setRerankTextFieldSuffix(index.getRerankTextFieldSuffix());
        getView().setRerankScoreMinimum(index.getRerankScoreMinimum());

        pipelinePresenter.setSelectedEntityReference(index.getDefaultExtractionPipeline(), true);
    }

    @Override
    protected ElasticIndexDoc onWrite(final ElasticIndexDoc index) {
        index.setClusterRef(clusterPresenter.getSelectedEntityReference());
        index.setVectorGenerationModelRef(vectorGenerationModelPresenter.getSelectedEntityReference());
        index.setRerankModelRef(rerankModelPresenter.getSelectedEntityReference());

        final String indexName = getView().getIndexName().trim();
        if (indexName.isEmpty()) {
            index.setIndexName(null);
        } else {
            index.setIndexName(indexName);
        }

        index.setSearchSlices(getView().getSearchSlices());
        index.setSearchScrollSize(getView().getSearchScrollSize());
        index.setTimeField(getView().getTimeField());
        index.setRerankTextFieldSuffix(getView().getRerankTextFieldSuffix());
        index.setRerankScoreMinimum(getView().getRerankScoreMinimum());
        index.setDefaultExtractionPipeline(pipelinePresenter.getSelectedEntityReference());
        return index;
    }

    @Override
    public synchronized void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        super.setTaskMonitorFactory(taskMonitorFactory);
        fieldSelectionBoxModel.setTaskMonitorFactory(taskMonitorFactory);
    }

    public interface ElasticIndexSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<ElasticIndexSettingsUiHandlers> {

        void setClusterView(final View view);

        String getIndexName();

        void setIndexName(final String indexName);

        int getSearchSlices();

        void setSearchSlices(final int searchSlices);

        int getSearchScrollSize();

        void setSearchScrollSize(final int searchScrollSize);

        String getTimeField();

        void setTimeField(String partitionTimeField);

        String getRerankTextFieldSuffix();

        void setRerankTextFieldSuffix(String rerankTextFieldSuffix);

        Float getRerankScoreMinimum();

        void setRerankScoreMinimum(Float rerankScoreMinimum);

        void setVectorGenerationModelView(final View view);

        void setRerankModelView(final View view);

        void setDefaultExtractionPipelineView(View view);
    }
}

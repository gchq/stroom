/*
 * Copyright 2019 Crown Copyright
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
import stroom.entity.client.presenter.DocPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
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

public class ElasticIndexSettingsPresenter extends DocPresenter<ElasticIndexSettingsView, ElasticIndexDoc>
        implements ElasticIndexSettingsUiHandlers {

    private static final ElasticIndexResource ELASTIC_INDEX_RESOURCE = GWT.create(ElasticIndexResource.class);

    private final DocSelectionBoxPresenter clusterPresenter;
    private final DocSelectionBoxPresenter pipelinePresenter;
    private final RestFactory restFactory;
    private final DynamicFieldSelectionListModel fieldSelectionBoxModel;

    @Inject
    public ElasticIndexSettingsPresenter(
            final EventBus eventBus,
            final ElasticIndexSettingsView view,
            final DocSelectionBoxPresenter clusterPresenter,
            final DocSelectionBoxPresenter pipelinePresenter,
            final RestFactory restFactory,
            final DynamicFieldSelectionListModel fieldSelectionBoxModel) {
        super(eventBus, view);

        this.clusterPresenter = clusterPresenter;
        this.pipelinePresenter = pipelinePresenter;
        this.restFactory = restFactory;
        this.fieldSelectionBoxModel = fieldSelectionBoxModel;

        clusterPresenter.setIncludedTypes(ElasticClusterDoc.TYPE);
        clusterPresenter.setRequiredPermissions(DocumentPermission.USE);
        pipelinePresenter.setIncludedTypes(PipelineDoc.TYPE);
        pipelinePresenter.setRequiredPermissions(DocumentPermission.VIEW);

        view.setUiHandlers(this);
        view.setDefaultExtractionPipelineView(pipelinePresenter.getView());
        view.setClusterView(clusterPresenter.getView());
    }

    @Override
    protected void onBind() {
        // If the selected `ElasticCluster` changes, set the dirty flag to `true`
        registerHandler(clusterPresenter.addDataSelectionHandler(event -> onChange()));
        registerHandler(pipelinePresenter.addDataSelectionHandler(selection -> onChange()));
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
        getView().setIndexName(index.getIndexName());
        getView().setSearchSlices(index.getSearchSlices());
        getView().setSearchScrollSize(index.getSearchScrollSize());
        getView().setTimeField(index.getTimeField());

        pipelinePresenter.setSelectedEntityReference(index.getDefaultExtractionPipeline(), true);
    }

    @Override
    protected ElasticIndexDoc onWrite(final ElasticIndexDoc index) {
        return index
                .copy()
                .clusterRef(clusterPresenter.getSelectedEntityReference())
                .indexName(getView().getIndexName().trim())
                .searchSlices(getView().getSearchSlices())
                .searchScrollSize(getView().getSearchScrollSize())
                .timeField(getView().getTimeField())
                .defaultExtractionPipeline(pipelinePresenter.getSelectedEntityReference())
                .build();
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

        void setDefaultExtractionPipelineView(View view);
    }
}

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

package stroom.search.solr.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.component.ExpressionEditorController;
import stroom.query.client.presenter.DynamicFieldSelectionListModel;
import stroom.search.solr.client.presenter.SolrIndexSettingsPresenter.SolrIndexSettingsView;
import stroom.search.solr.shared.SolrConnectionConfig;
import stroom.search.solr.shared.SolrConnectionConfig.InstanceType;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.search.solr.shared.SolrIndexResource;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Contexts;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.View;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.UUID;

public class SolrIndexSettingsPresenter
        extends DocPresenter<SolrIndexSettingsView, SolrIndexDoc>
        implements SolrIndexSettingsUiHandlers {

    private static final SolrIndexResource SOLR_INDEX_RESOURCE = GWT.create(SolrIndexResource.class);

    private final ExpressionEditorController editExpressionPresenter;
    private final DocSelectionBoxPresenter pipelinePresenter;
    private final RestFactory restFactory;
    private final DynamicFieldSelectionListModel fieldSelectionBoxModel;

    @Inject
    public SolrIndexSettingsPresenter(final EventBus eventBus,
                                      final SolrIndexSettingsView view,
                                      final ExpressionEditorController editExpressionPresenter,
                                      final DocSelectionBoxPresenter pipelinePresenter,
                                      final RestFactory restFactory,
                                      final DynamicFieldSelectionListModel fieldSelectionBoxModel) {
        super(eventBus, view);
        // Read only as its document is
        watch(Contexts.READ_ONLY, getView()::onReadOnly);
        own(editExpressionPresenter);
        own(pipelinePresenter);
        this.editExpressionPresenter = editExpressionPresenter;
        this.pipelinePresenter = pipelinePresenter;
        this.restFactory = restFactory;
        this.fieldSelectionBoxModel = fieldSelectionBoxModel;
        // Shows its requests' progress as this presenter does (its context's)
        fieldSelectionBoxModel.setTaskMonitorFactory(this);

        pipelinePresenter.setIncludedTypes(PipelineDoc.TYPE);
        pipelinePresenter.setRequiredPermissions(DocumentPermission.VIEW);

        view.setUiHandlers(this);
        view.setDefaultExtractionPipelineView(pipelinePresenter.getView());
        view.setRetentionExpressionView(editExpressionPresenter);

        editExpressionPresenter.setOnChange(this::onChange);
        own(pipelinePresenter.addSelectionListener(selection -> onChange()));
    }

    @Override
    public void onTestConnection(final TaskMonitorFactory taskMonitorFactory) {
        getView().setTestingConnection(true);
        final SolrIndexDoc index = onWrite(SolrIndexDoc.builder().uuid(UUID.randomUUID().toString()).build());
        restFactory
                .create(SOLR_INDEX_RESOURCE)
                .method(res -> res.solrConnectionTest(index))
                .onSuccess(result -> {
                    if (result.isOk()) {
                        AlertEvent.fireInfo(this, "Connection Success", result.getMessage(), () ->
                                getView().setTestingConnection(false));
                    } else {
                        AlertEvent.fireError(this, "Connection Failure", result.getMessage(), () ->
                                getView().setTestingConnection(false));
                    }
                })
                .onFailure(new DefaultErrorHandler(this, () -> getView().setTestingConnection(false)))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    protected void onRead(final DocRef docRef, final SolrIndexDoc index) {
        final SolrConnectionConfig connectionConfig = index.getSolrConnectionConfig();
        if (connectionConfig != null) {
            getView().setInstanceType(connectionConfig.getInstanceType());
            getView().setSolrUrls(connectionConfig.getSolrUrls());
            getView().setZkHosts(connectionConfig.getZkHosts());
            getView().setZkPath(connectionConfig.getZkPath());
            getView().setUseZk(connectionConfig.isUseZk());
        }

        getView().setCollection(index.getCollection());
        getView().setTimeField(index.getTimeField());

        ExpressionOperator retentionExpression = index.getRetentionExpression();
        if (retentionExpression == null) {
            retentionExpression = ExpressionOperator.builder().build();
        }

        fieldSelectionBoxModel.setDataSourceRefConsumer(consumer -> consumer.accept(docRef));
        editExpressionPresenter.init(docRef, fieldSelectionBoxModel);
        editExpressionPresenter.read(retentionExpression);
        editExpressionPresenter.setReadOnly(isReadOnly());
        pipelinePresenter.setSelectedEntityReference(index.getDefaultExtractionPipeline(), true);
        pipelinePresenter.setReadOnly(isReadOnly());
    }

    @Override
    protected SolrIndexDoc onWrite(final SolrIndexDoc index) {
        final SolrConnectionConfig connectionConfig = SolrConnectionConfig
                .builder()
                .instanceType(getView().getInstanceType())
                .solrUrls(getView().getSolrUrls())
                .zkHosts(getView().getZkHosts())
                .zkPath(getView().getZkPath())
                .useZk(getView().isUseZk())
                .build();

        String collection = null;
        if (!NullSafe.isBlankString(getView().getCollection())) {
            collection = getView().getCollection().trim();
        }

        return index
                .copy()
                .collection(collection)
                .timeField(getView().getTimeField())
                .solrConnectionConfig(connectionConfig)
                .retentionExpression(editExpressionPresenter.write())
                .defaultExtractionPipeline(pipelinePresenter.getSelectedEntityReference())
                .build();
    }

    public interface SolrIndexSettingsView
            extends View, HasUiHandlers<SolrIndexSettingsUiHandlers> {

        /// Shows whether the user may change what this shows.
        ///
        /// @param readOnly Whether it may not be changed.
        void onReadOnly(boolean readOnly);

        String getCollection();

        void setCollection(String collection);

        InstanceType getInstanceType();

        void setInstanceType(InstanceType instanceType);

        List<String> getSolrUrls();

        void setSolrUrls(List<String> solrUrls);

        boolean isUseZk();

        void setUseZk(boolean useZk);

        List<String> getZkHosts();

        void setZkHosts(List<String> zkHosts);

        String getZkPath();

        void setZkPath(String zkPath);

        void setRetentionExpressionView(final IsWidget view);

        String getTimeField();

        void setTimeField(String partitionTimeField);

        void setDefaultExtractionPipelineView(View view);

        void setTestingConnection(boolean testing);
    }
}

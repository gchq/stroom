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

package stroom.view.client.presenter;

import stroom.data.client.presenter.EditExpressionPresenter;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.NodeFlag;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.presenter.SimpleFieldSelectionListModel;
import stroom.security.shared.DocumentPermission;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.QueryConfig;
import stroom.util.shared.NullSafe;
import stroom.view.client.presenter.ViewSettingsPresenter.ViewSettingsView;
import stroom.view.shared.ViewDoc;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class ViewSettingsPresenter extends DocumentEditPresenter<ViewSettingsView, ViewDoc> {

    private final RestFactory restFactory;
    private final DocSelectionBoxPresenter dataSourceSelectionPresenter;
    private final DocSelectionBoxPresenter pipelineSelectionPresenter;
    private final EditExpressionPresenter expressionPresenter;

    @Inject
    public ViewSettingsPresenter(final EventBus eventBus,
                                 final ViewSettingsView view,
                                 final RestFactory restFactory,
                                 final DocSelectionBoxPresenter dataSourceSelectionPresenter,
                                 final DocSelectionBoxPresenter pipelineSelectionPresenter,
                                 final EditExpressionPresenter expressionPresenter,
                                 final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.dataSourceSelectionPresenter = dataSourceSelectionPresenter;
        this.pipelineSelectionPresenter = pipelineSelectionPresenter;
        this.expressionPresenter = expressionPresenter;

        view.setDataSourceSelectionView(dataSourceSelectionPresenter.getView());
        view.setPipelineSelectionView(pipelineSelectionPresenter.getView());
        view.setExpressionView(expressionPresenter.getView());

        dataSourceSelectionPresenter.setNodeFlags(NodeFlag.DATA_SOURCE);
        dataSourceSelectionPresenter.setRequiredPermissions(DocumentPermission.USE);
        dataSourceSelectionPresenter.setItemType("Data Source");

        pipelineSelectionPresenter.setIncludedTypes(PipelineDoc.TYPE);
        pipelineSelectionPresenter.setRequiredPermissions(DocumentPermission.USE);

        // Filter the pipeline picker by tags, if configured
        uiConfigCache.get(extendedUiConfig -> {
            if (extendedUiConfig != null) {
                NullSafe.consume(
                        extendedUiConfig.getQuery(),
                        QueryConfig::getViewPipelineSelectorIncludedTags,
                        ExplorerTreeFilter::createTagQuickFilterInput,
                        pipelineSelectionPresenter::setQuickFilter);
            }
        }, this);
    }

    @Override
    protected void onBind() {
        registerHandlers();
    }

    private void registerHandlers() {
        registerHandler(dataSourceSelectionPresenter.addDataSelectionHandler(event -> setDirty(true)));
        registerHandler(pipelineSelectionPresenter.addDataSelectionHandler(event -> setDirty(true)));
        registerHandler(expressionPresenter.addDirtyHandler(event -> setDirty(true)));
    }

    @Override
    protected void onRead(final DocRef docRef, final ViewDoc entity, final boolean readOnly) {
        dataSourceSelectionPresenter.setSelectedEntityReference(entity.getDataSource(), true);
        pipelineSelectionPresenter.setSelectedEntityReference(entity.getPipeline(), true);
        final SimpleFieldSelectionListModel fieldSelectionBoxModel = new SimpleFieldSelectionListModel();
        fieldSelectionBoxModel.addItems(MetaFields.getAllFields());
        expressionPresenter.init(restFactory, MetaFields.STREAM_STORE_DOC_REF, fieldSelectionBoxModel);

        // Read expression.
        ExpressionOperator root = entity.getFilter();
        if (root == null) {
            root = ExpressionOperator.builder().build();
        }
        expressionPresenter.read(root);
    }

    @Override
    protected ViewDoc onWrite(final ViewDoc entity) {
        entity.setDataSource(dataSourceSelectionPresenter.getSelectedEntityReference());
        entity.setPipeline(pipelineSelectionPresenter.getSelectedEntityReference());
        entity.setFilter(expressionPresenter.write());
        return entity;
    }


    // --------------------------------------------------------------------------------


    public interface ViewSettingsView extends View {

        void setDataSourceSelectionView(View view);

        void setExpressionView(View view);

        void setPipelineSelectionView(View view);
    }
}

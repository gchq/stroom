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

package stroom.view.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.explorer.shared.StandardTagNames;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.shared.DocumentPermissionNames;
import stroom.view.client.presenter.ViewSettingsPresenter.ViewSettingsView;
import stroom.view.shared.ViewDoc;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;

public class ViewSettingsPresenter extends DocumentEditPresenter<ViewSettingsView, ViewDoc> {

    private final RestFactory restFactory;
    private final EntityDropDownPresenter dataSourceSelectionPresenter;
    private final EntityDropDownPresenter pipelineSelectionPresenter;
//    private final EditExpressionPresenter expressionPresenter;

    @Inject
    public ViewSettingsPresenter(final EventBus eventBus,
                                 final ViewSettingsView view,
                                 final RestFactory restFactory,
                                 final EntityDropDownPresenter dataSourceSelectionPresenter,
                                 final EntityDropDownPresenter pipelineSelectionPresenter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.dataSourceSelectionPresenter = dataSourceSelectionPresenter;
        this.pipelineSelectionPresenter = pipelineSelectionPresenter;
//        this.expressionPresenter = expressionPresenter;

        view.setDataSourceSelectionView(dataSourceSelectionPresenter.getView());
        view.setPipelineSelectionView(pipelineSelectionPresenter.getView());
//        view.setExpressionView(expressionPresenter.getView());

        dataSourceSelectionPresenter.setTags(StandardTagNames.DATA_SOURCE);
        dataSourceSelectionPresenter.setRequiredPermissions(DocumentPermissionNames.USE);

        pipelineSelectionPresenter.setIncludedTypes(PipelineDoc.DOCUMENT_TYPE);
        pipelineSelectionPresenter.setRequiredPermissions(DocumentPermissionNames.USE);
    }

    @Override
    protected void onBind() {
        registerHandler(dataSourceSelectionPresenter.addDataSelectionHandler(event -> {
            if (!Objects.equals(getEntity().getDataSource(),
                    dataSourceSelectionPresenter.getSelectedEntityReference())) {
                setDirty(true);
            }
        }));
        registerHandler(pipelineSelectionPresenter.addDataSelectionHandler(event -> {
            if (!Objects.equals(getEntity().getPipeline(),
                    pipelineSelectionPresenter.getSelectedEntityReference())) {
                setDirty(true);
            }
        }));
//        registerHandler(expressionPresenter.addDirtyHandler(event -> setDirty(true)));
    }

    @Override
    protected void onRead(final DocRef docRef, final ViewDoc entity, final boolean readOnly) {
        dataSourceSelectionPresenter.setSelectedEntityReference(entity.getDataSource());
        pipelineSelectionPresenter.setSelectedEntityReference(entity.getPipeline());

//        expressionPresenter.init(restFactory, MetaFields.STREAM_STORE_DOC_REF, MetaFields.getAllFields());
//
//        // Read expression.
//        ExpressionOperator root = entity.getFilter();
//        if (root == null) {
//            root = ExpressionOperator.builder().build();
//        }
//        expressionPresenter.read(root);
    }

    @Override
    protected ViewDoc onWrite(final ViewDoc entity) {
        entity.setDataSource(dataSourceSelectionPresenter.getSelectedEntityReference());
        entity.setPipeline(pipelineSelectionPresenter.getSelectedEntityReference());
//        entity.setFilter(expressionPresenter.write());
        return entity;
    }

    public interface ViewSettingsView extends View {

        void setDataSourceSelectionView(View view);

//        void setExpressionView(View view);

        void setPipelineSelectionView(View view);
    }
}

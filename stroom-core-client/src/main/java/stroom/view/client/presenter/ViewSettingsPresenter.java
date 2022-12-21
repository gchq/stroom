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

import stroom.data.client.presenter.EditExpressionPresenter;
import stroom.datasource.api.v2.AbstractField;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentSettingsPresenter;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.explorer.shared.StandardTagNames;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.v2.ExpressionOperator;
import stroom.security.shared.DocumentPermissionNames;
import stroom.view.client.presenter.ViewSettingsPresenter.ViewSettingsView;
import stroom.view.shared.ViewDoc;

import com.google.gwt.event.dom.client.InputEvent;
import com.google.gwt.event.dom.client.InputHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ViewSettingsPresenter extends DocumentSettingsPresenter<ViewSettingsView, ViewDoc> {

    private final RestFactory restFactory;
    private final IndexLoader indexLoader;
    private final EntityDropDownPresenter dataSourceSelectionPresenter;
    private final EntityDropDownPresenter pipelineSelectionPresenter;
    private final EditExpressionPresenter expressionPresenter;

    @Inject
    public ViewSettingsPresenter(final EventBus eventBus,
                                 final ViewSettingsView view,
                                 final IndexLoader indexLoader,
                                 final RestFactory restFactory,
                                 final EntityDropDownPresenter dataSourceSelectionPresenter,
                                 final EntityDropDownPresenter pipelineSelectionPresenter,
                                 final EditExpressionPresenter expressionPresenter) {
        super(eventBus, view);
        this.indexLoader = indexLoader;
        this.restFactory = restFactory;
        this.dataSourceSelectionPresenter = dataSourceSelectionPresenter;
        this.pipelineSelectionPresenter = pipelineSelectionPresenter;
        this.expressionPresenter = expressionPresenter;

        view.setDataSourceSelectionView(dataSourceSelectionPresenter.getView());
        view.setPipelineSelectionView(pipelineSelectionPresenter.getView());
        view.setExpressionView(expressionPresenter.getView());

        dataSourceSelectionPresenter.setTags(StandardTagNames.DATA_SOURCE);
        dataSourceSelectionPresenter.setRequiredPermissions(DocumentPermissionNames.USE);

        pipelineSelectionPresenter.setIncludedTypes(PipelineDoc.DOCUMENT_TYPE);
        pipelineSelectionPresenter.setRequiredPermissions(DocumentPermissionNames.USE);

        // Add listeners for dirty events.
        final InputHandler inputHandler = event -> setDirty(true);
        registerHandler(view.getDescription().addDomHandler(inputHandler, InputEvent.getType()));
    }

    @Override
    protected void onBind() {
        registerHandler(indexLoader.addChangeDataHandler(event ->
                loadedDataSource(indexLoader.getLoadedDataSourceRef(), indexLoader.getDataSourceFieldsMap())));
        registerHandler(dataSourceSelectionPresenter.addDataSelectionHandler(event -> {
            if (!Objects.equals(indexLoader.getLoadedDataSourceRef(),
                    dataSourceSelectionPresenter.getSelectedEntityReference())) {
                setDirty(true);
                indexLoader.loadDataSource(dataSourceSelectionPresenter.getSelectedEntityReference());
            }
        }));
        registerHandler(pipelineSelectionPresenter.addDataSelectionHandler(event -> {
            if (!Objects.equals(getEntity().getPipeline(),
                    pipelineSelectionPresenter.getSelectedEntityReference())) {
                setDirty(true);
            }
        }));
        registerHandler(expressionPresenter.addDirtyHandler(event -> setDirty(true)));
    }

    private void loadedDataSource(final DocRef dataSourceRef, final DataSourceFieldsMap dataSourceFieldsMap) {
        // Create a list of index fields.
        final List<AbstractField> fields = new ArrayList<>();
        if (dataSourceFieldsMap != null) {
            for (final AbstractField field : dataSourceFieldsMap.values()) {
                // Protection from default values of false not being in the serialised json
                if (field.getQueryable() != null
                        ? field.getQueryable()
                        : false) {
                    fields.add(field);
                }
            }
        }
        fields.sort(Comparator.comparing(AbstractField::getName, String.CASE_INSENSITIVE_ORDER));
        expressionPresenter.init(restFactory, dataSourceRef, fields);
    }

    @Override
    protected void onRead(final DocRef docRef, final ViewDoc entity) {
        getView().getDescription().setText(entity.getDescription());
        dataSourceSelectionPresenter.setSelectedEntityReference(entity.getDataSource());
        pipelineSelectionPresenter.setSelectedEntityReference(entity.getPipeline());

        // Read data source.
        indexLoader.loadDataSource(entity.getDataSource());

        // Read expression.
        ExpressionOperator root = entity.getFilter();
        if (root == null) {
            root = ExpressionOperator.builder().build();
        }
        expressionPresenter.read(root);
    }

    @Override
    protected void onWrite(final ViewDoc entity) {
        entity.setDescription(getView().getDescription().getText().trim());
        entity.setDataSource(dataSourceSelectionPresenter.getSelectedEntityReference());
        entity.setPipeline(pipelineSelectionPresenter.getSelectedEntityReference());
        entity.setFilter(expressionPresenter.write());
    }

    @Override
    public String getType() {
        return ViewDoc.DOCUMENT_TYPE;
    }

    public interface ViewSettingsView extends View {

        TextArea getDescription();

        void setDataSourceSelectionView(View view);

        void setExpressionView(View view);

        void setPipelineSelectionView(View view);
    }
}

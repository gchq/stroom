/*
 * Copyright 2017 Crown Copyright
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

package stroom.search.elastic.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.presenter.EditExpressionPresenter;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentSettingsPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.search.elastic.client.presenter.ElasticIndexSettingsPresenter.ElasticIndexSettingsView;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDataSourceFieldUtil;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexResource;
import stroom.search.elastic.shared.ElasticIndexTestResponse;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.EqualsUtil;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class ElasticIndexSettingsPresenter extends DocumentSettingsPresenter<ElasticIndexSettingsView, ElasticIndexDoc>
        implements ElasticIndexSettingsUiHandlers {

    private static final ElasticIndexResource ELASTIC_INDEX_RESOURCE = GWT.create(ElasticIndexResource.class);

    private final EntityDropDownPresenter clusterPresenter;
    private final EditExpressionPresenter editExpressionPresenter;
    private final RestFactory restFactory;

    @Inject
    public ElasticIndexSettingsPresenter(
            final EventBus eventBus,
            final ElasticIndexSettingsView view,
            final EntityDropDownPresenter clusterPresenter,
            final EditExpressionPresenter editExpressionPresenter,
            final RestFactory restFactory
    ) {
        super(eventBus, view);

        this.clusterPresenter = clusterPresenter;
        this.editExpressionPresenter = editExpressionPresenter;
        this.restFactory = restFactory;

        clusterPresenter.setIncludedTypes(ElasticClusterDoc.DOCUMENT_TYPE);
        clusterPresenter.setRequiredPermissions(DocumentPermissionNames.USE);

        view.setUiHandlers(this);
        view.setClusterView(clusterPresenter.getView());
        view.setRetentionExpressionView(editExpressionPresenter.getView());
    }

    @Override
    protected void onBind() {
        // If the selected `ElasticCluster` changes, set the dirty flag to `true`
        registerHandler(clusterPresenter.addDataSelectionHandler(event -> {
            if (!EqualsUtil.isEquals(clusterPresenter.getSelectedEntityReference(), getEntity().getClusterRef())) {
                setDirty(true);
            }
        }));

        registerHandler(editExpressionPresenter.addDirtyHandler(dirty -> setDirty(true)));
    }

    @Override
    public void onChange() {
        setDirty(true);
    }

    @Override
    public void onTestIndex() {
        final ElasticIndexDoc index = new ElasticIndexDoc();
        onWrite(index);

        final Rest<ElasticIndexTestResponse> rest = restFactory.create();
        rest
                .onSuccess(result -> {
                    if (result.isOk()) {
                        AlertEvent.fireInfo(this, "Connection Success", result.getMessage(), null);
                    } else {
                        AlertEvent.fireError(this, "Connection Failure", result.getMessage(), null);
                    }
                })
                .call(ELASTIC_INDEX_RESOURCE)
                .testIndex(index);
    }

    @Override
    public String getType() {
        return ElasticIndexDoc.DOCUMENT_TYPE;
    }

    @Override
    protected void onRead(final DocRef docRef, final ElasticIndexDoc index) {
        getView().setDescription(index.getDescription());
        clusterPresenter.setSelectedEntityReference(index.getClusterRef());
        getView().setIndexName(index.getIndexName());

        if (index.getRetentionExpression() == null) {
            index.setRetentionExpression(ExpressionOperator.builder().op(Op.AND).build());
        }

        editExpressionPresenter.init(restFactory, docRef, ElasticIndexDataSourceFieldUtil.getDataSourceFields(index));
        editExpressionPresenter.read(index.getRetentionExpression());
    }

    @Override
    protected void onWrite(final ElasticIndexDoc index) {
        index.setDescription(getView().getDescription().trim());
        index.setClusterRef(clusterPresenter.getSelectedEntityReference());

        final String indexName = getView().getIndexName().trim();
        if (indexName.isEmpty()) {
            index.setIndexName(null);
        } else {
            index.setIndexName(indexName);
        }

        index.setRetentionExpression(editExpressionPresenter.write());
    }

    public interface ElasticIndexSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<ElasticIndexSettingsUiHandlers> {
        String getDescription();

        void setDescription(String description);

        void setClusterView(final View view);

        String getIndexName();

        void setIndexName(String indexName);

        void setRetentionExpressionView(final View view);
    }
}

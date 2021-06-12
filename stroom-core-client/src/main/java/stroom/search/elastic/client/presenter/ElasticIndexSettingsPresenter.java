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
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.DocumentSettingsPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.ruleset.client.presenter.EditExpressionPresenter;
import stroom.search.elastic.client.presenter.ElasticIndexSettingsPresenter.ElasticIndexSettingsView;
import stroom.search.elastic.shared.ElasticCluster;
import stroom.search.elastic.shared.ElasticConnectionTestAction;
import stroom.search.elastic.shared.ElasticIndex;
import stroom.search.elastic.shared.ElasticIndexDataSourceFieldUtil;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.EqualsUtil;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class ElasticIndexSettingsPresenter extends DocumentSettingsPresenter<ElasticIndexSettingsView, ElasticIndex> implements ElasticIndexSettingsUiHandlers {
    private final EntityDropDownPresenter clusterPresenter;
    private final EditExpressionPresenter editExpressionPresenter;
    private final ClientDispatchAsync dispatcher;

    @Inject
    public ElasticIndexSettingsPresenter(final EventBus eventBus,
                                         final ElasticIndexSettingsView view,
                                         final EntityDropDownPresenter clusterPresenter,
                                         final EditExpressionPresenter editExpressionPresenter,
                                         final ClientDispatchAsync dispatcher
    ) {
        super(eventBus, view);

        this.clusterPresenter = clusterPresenter;
        this.editExpressionPresenter = editExpressionPresenter;
        this.dispatcher = dispatcher;

        clusterPresenter.setIncludedTypes(ElasticCluster.ENTITY_TYPE);
        clusterPresenter.setRequiredPermissions(DocumentPermissionNames.USE);

        view.setUiHandlers(this);
        view.setClusterView(clusterPresenter.getView());
        view.setRententionExpressionView(editExpressionPresenter.getView());
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
    public void onTestConnection() {
        final ElasticIndex index = new ElasticIndex();
        onWrite(index);

        dispatcher.exec(new ElasticConnectionTestAction(index)).onSuccess(result -> AlertEvent.fireInfo(this, "Success", result.toString(), null));
    }

    @Override
    public String getType() {
        return ElasticIndex.ENTITY_TYPE;
    }

    @Override
    protected void onRead(final DocRef docRef, final ElasticIndex index) {
        getView().setDescription(index.getDescription());
        clusterPresenter.setSelectedEntityReference(index.getClusterRef());
        getView().setIndexName(index.getIndexName());

        if (index.getRetentionExpression() == null) {
            index.setRetentionExpression(new ExpressionOperator.Builder().op(Op.AND).build());
        }

        editExpressionPresenter.init(dispatcher, docRef, ElasticIndexDataSourceFieldUtil.getDataSourceFields(index));
        editExpressionPresenter.read(index.getRetentionExpression());
    }

    @Override
    protected void onWrite(final ElasticIndex index) {
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

    public interface ElasticIndexSettingsView extends View, ReadOnlyChangeHandler, HasUiHandlers<ElasticIndexSettingsUiHandlers> {
        String getDescription();

        void setDescription(String description);

        void setClusterView(final View view);

        String getIndexName();

        void setIndexName(String indexName);

        void setRententionExpressionView(final View view);
    }
}

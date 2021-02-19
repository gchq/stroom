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
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.ruleset.client.presenter.EditExpressionPresenter;
import stroom.search.elastic.client.presenter.ElasticIndexSettingsPresenter.ElasticIndexSettingsView;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndex;
import stroom.search.solr.shared.SolrConnectionConfig.InstanceType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class ElasticIndexSettingsPresenter extends DocumentSettingsPresenter<ElasticIndexSettingsView, ElasticIndex> implements ElasticIndexSettingsUiHandlers {
    private final EditExpressionPresenter editExpressionPresenter;
    private final ClientDispatchAsync dispatcher;

    @Inject
    public ElasticIndexSettingsPresenter(final EventBus eventBus,
                                         final ElasticIndexSettingsView view,
                                         final EditExpressionPresenter editExpressionPresenter,
                                         final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.editExpressionPresenter = editExpressionPresenter;
        this.dispatcher = dispatcher;

        view.setUiHandlers(this);
        view.setRententionExpressionView(editExpressionPresenter.getView());
    }

    @Override
    protected void onBind() {
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
        final ElasticConnectionConfig connectionConfig = index.getElasticConnectionConfig();
        if (connectionConfig != null) {
            getView().setInstanceType(connectionConfig.getInstanceType());
            getView().setElasticUrls(connectionConfig.getElasticUrls());
            getView().setZkHosts(connectionConfig.getZkHosts());
            getView().setZkPath(connectionConfig.getZkPath());
            getView().setUseZk(connectionConfig.isUseZk());
        }

        getView().setDescription(index.getDescription());
        getView().setCollection(index.getCollection());

        if (index.getRetentionExpression() == null) {
            index.setRetentionExpression(new ExpressionOperator.Builder().op(Op.AND).build());
        }
        editExpressionPresenter.init(dispatcher, docRef, ElasticIndexDataSourceFieldUtil.getDataSourceFields(index));
        editExpressionPresenter.read(index.getRetentionExpression());
    }

    @Override
    protected void onWrite(final ElasticIndex index) {
        final ElasticConnectionConfig connectionConfig = new ElasticConnectionConfig();
        connectionConfig.setInstanceType(getView().getInstanceType());
        connectionConfig.setElasticUrls(getView().getElasticUrls());
        connectionConfig.setZkHosts(getView().getZkHosts());
        connectionConfig.setZkPath(getView().getZkPath());
        connectionConfig.setUseZk(getView().isUseZk());
        index.setElasticConnectionConfig(connectionConfig);

        index.setDescription(getView().getDescription().trim());
        if (getView().getCollection().trim().length() == 0) {
            index.setCollection(null);
        } else {
            index.setCollection(getView().getCollection().trim());
        }
        index.setRetentionExpression(editExpressionPresenter.write());
    }

    public interface ElasticIndexSettingsView extends View, ReadOnlyChangeHandler, HasUiHandlers<ElasticIndexSettingsUiHandlers> {
        String getDescription();

        void setDescription(String description);

        String getCollection();

        void setCollection(String collection);

        InstanceType getInstanceType();

        void setInstanceType(InstanceType instanceType);

        List<String> getElasticUrls();

        void setElasticUrls(List<String> ElasticUrls);

        boolean isUseZk();

        void setUseZk(boolean useZk);

        List<String> getZkHosts();

        void setZkHosts(List<String> zkHosts);

        String getZkPath();

        void setZkPath(String zkPath);

        void setRententionExpressionView(final View view);
    }
}

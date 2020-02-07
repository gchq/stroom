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

package stroom.search.solr.client.presenter;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentSettingsPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.receive.rules.client.presenter.EditExpressionPresenter;
import stroom.search.solr.client.presenter.SolrIndexSettingsPresenter.SolrIndexSettingsView;
import stroom.search.solr.shared.SolrConnectionConfig;
import stroom.search.solr.shared.SolrConnectionConfig.InstanceType;
import stroom.search.solr.shared.SolrIndexDataSourceFieldUtil;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.search.solr.shared.SolrIndexResource;

import java.util.List;

public class SolrIndexSettingsPresenter extends DocumentSettingsPresenter<SolrIndexSettingsView, SolrIndexDoc> implements SolrIndexSettingsUiHandlers {
    private static final SolrIndexResource SOLR_INDEX_RESOURCE = GWT.create(SolrIndexResource.class);

    private final EditExpressionPresenter editExpressionPresenter;
    private final RestFactory restFactory;

    @Inject
    public SolrIndexSettingsPresenter(final EventBus eventBus,
                                      final SolrIndexSettingsView view,
                                      final EditExpressionPresenter editExpressionPresenter,
                                      final RestFactory restFactory) {
        super(eventBus, view);
        this.editExpressionPresenter = editExpressionPresenter;
        this.restFactory = restFactory;

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
        final SolrIndexDoc index = new SolrIndexDoc();
        onWrite(index);

        final Rest<String> rest = restFactory.create();
        rest
                .onSuccess(result -> AlertEvent.fireInfo(this, "Success", result.toString(), null))
                .call(SOLR_INDEX_RESOURCE)
                .solrConnectionTest(index);
    }

    @Override
    public String getType() {
        return SolrIndexDoc.DOCUMENT_TYPE;
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

        getView().setDescription(index.getDescription());
        getView().setCollection(index.getCollection());

        if (index.getRetentionExpression() == null) {
            index.setRetentionExpression(new ExpressionOperator.Builder().op(Op.AND).build());
        }
        editExpressionPresenter.init(restFactory, docRef, SolrIndexDataSourceFieldUtil.getDataSourceFields(index));
        editExpressionPresenter.read(index.getRetentionExpression());
    }

    @Override
    protected void onWrite(final SolrIndexDoc index) {
        final SolrConnectionConfig connectionConfig = new SolrConnectionConfig();
        connectionConfig.setInstanceType(getView().getInstanceType());
        connectionConfig.setSolrUrls(getView().getSolrUrls());
        connectionConfig.setZkHosts(getView().getZkHosts());
        connectionConfig.setZkPath(getView().getZkPath());
        connectionConfig.setUseZk(getView().isUseZk());
        index.setSolrConnectionConfig(connectionConfig);

        index.setDescription(getView().getDescription().trim());
        if (getView().getCollection().trim().length() == 0) {
            index.setCollection(null);
        } else {
            index.setCollection(getView().getCollection().trim());
        }
        index.setRetentionExpression(editExpressionPresenter.write());
    }

    public interface SolrIndexSettingsView extends View, ReadOnlyChangeHandler, HasUiHandlers<SolrIndexSettingsUiHandlers> {
        String getDescription();

        void setDescription(String description);

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

        void setRententionExpressionView(final View view);
    }
}

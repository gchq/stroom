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
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentSettingsPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.search.elastic.client.presenter.ElasticClusterSettingsPresenter.ElasticClusterSettingsView;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticClusterResource;
import stroom.search.elastic.shared.ElasticClusterTestResponse;
import stroom.search.elastic.shared.ElasticConnectionConfig;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class ElasticClusterSettingsPresenter
        extends DocumentSettingsPresenter<ElasticClusterSettingsView, ElasticClusterDoc>
        implements ElasticClusterSettingsUiHandlers {
    private static final ElasticClusterResource ELASTIC_CLUSTER_RESOURCE = GWT.create(ElasticClusterResource.class);

    private final RestFactory restFactory;

    @Inject
    public ElasticClusterSettingsPresenter(
            final EventBus eventBus,
            final ElasticClusterSettingsView view,
            final RestFactory restFactory
    ) {
        super(eventBus, view);

        this.restFactory = restFactory;

        view.setUiHandlers(this);
    }

    @Override
    protected void onBind() { }

    @Override
    public void onChange() {
        setDirty(true);
    }

    @Override
    public void onTestConnection() {
        final ElasticClusterDoc cluster = new ElasticClusterDoc();
        onWrite(cluster);

        final Rest<ElasticClusterTestResponse> rest = restFactory.create();
        rest
                .onSuccess(result -> {
                    if (result.isOk()) {
                        AlertEvent.fireInfo(this, "Connection Success", result.getMessage(), null);
                    } else {
                        AlertEvent.fireError(this, "Connection Failure", result.getMessage(), null);
                    }
                })
                .call(ELASTIC_CLUSTER_RESOURCE)
                .testCluster(cluster);
    }

    @Override
    public String getType() {
        return ElasticClusterDoc.DOCUMENT_TYPE;
    }

    @Override
    protected void onRead(final DocRef docRef, final ElasticClusterDoc cluster) {
        final ElasticConnectionConfig connectionConfig = cluster.getConnection();

        if (connectionConfig != null) {
            getView().setConnectionUrls(connectionConfig.getConnectionUrls());
            getView().setCaCertificate(connectionConfig.getCaCertificate());
            getView().setUseAuthentication(connectionConfig.getUseAuthentication());
            getView().setApiKeyId(connectionConfig.getApiKeyId());
            getView().setApiKeySecret(connectionConfig.getApiKeySecret());
            getView().setSocketTimeoutMillis(connectionConfig.getSocketTimeoutMillis());
        }

        getView().setDescription(cluster.getDescription());
    }

    @Override
    protected void onWrite(final ElasticClusterDoc cluster) {
        final ElasticConnectionConfig connectionConfig = new ElasticConnectionConfig();
        connectionConfig.setConnectionUrls(getView().getConnectionUrls());
        connectionConfig.setCaCertificate(getView().getCaCertificate());
        connectionConfig.setUseAuthentication(getView().getUseAuthentication());
        connectionConfig.setApiKeyId(getView().getApiKeyId());
        connectionConfig.setApiKeySecret(getView().getApiKeySecret());
        connectionConfig.setSocketTimeoutMillis(getView().getSocketTimeoutMillis());

        cluster.setConnection(connectionConfig);
        cluster.setDescription(getView().getDescription().trim());
    }

    public interface ElasticClusterSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<ElasticClusterSettingsUiHandlers> {

        String getDescription();

        void setDescription(String description);

        List<String> getConnectionUrls();

        void setConnectionUrls(List<String> connectionUrls);

        String getCaCertificate();

        void setCaCertificate(String caCertificate);

        boolean getUseAuthentication();

        void setUseAuthentication(boolean useAuthentication);

        String getApiKeyId();

        void setApiKeyId(String apiKeyId);

        String getApiKeySecret();

        void setApiKeySecret(String apiKeySecret);

        int getSocketTimeoutMillis();

        void setSocketTimeoutMillis(int socketTimeoutMillis);
    }
}

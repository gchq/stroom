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
import stroom.search.elastic.client.presenter.ElasticClusterSettingsPresenter.ElasticClusterSettingsView;
import stroom.search.elastic.shared.ElasticCluster;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticConnectionTestAction;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class ElasticClusterSettingsPresenter extends DocumentSettingsPresenter<ElasticClusterSettingsView, ElasticCluster> implements ElasticClusterSettingsUiHandlers {
    private final ClientDispatchAsync dispatcher;

    @Inject
    public ElasticClusterSettingsPresenter(final EventBus eventBus,
                                           final ElasticClusterSettingsView view,
                                           final ClientDispatchAsync dispatcher
    ) {
        super(eventBus, view);

        this.dispatcher = dispatcher;

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
        final ElasticCluster cluster = new ElasticCluster();
        onWrite(cluster);

        dispatcher.exec(new ElasticConnectionTestAction(cluster)).onSuccess(result -> AlertEvent.fireInfo(this, "Success", result.toString(), null));
    }

    @Override
    public String getType() {
        return ElasticCluster.ENTITY_TYPE;
    }

    @Override
    protected void onRead(final DocRef docRef, final ElasticCluster cluster) {
        final ElasticConnectionConfig connectionConfig = cluster.getConnectionConfig();

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
    protected void onWrite(final ElasticCluster cluster) {
        final ElasticConnectionConfig connectionConfig = new ElasticConnectionConfig();
        connectionConfig.setConnectionUrls(getView().getConnectionUrls());
        connectionConfig.setCaCertificate(getView().getCaCertificate());
        connectionConfig.setUseAuthentication(getView().getUseAuthentication());
        connectionConfig.setApiKeyId(getView().getApiKeyId());
        connectionConfig.setApiKeySecret(getView().getApiKeySecret());
        connectionConfig.setSocketTimeoutMillis(getView().getSocketTimeoutMillis());

        cluster.setConnectionConfig(connectionConfig);
        cluster.setDescription(getView().getDescription().trim());
    }

    public interface ElasticClusterSettingsView extends View, ReadOnlyChangeHandler, HasUiHandlers<ElasticClusterSettingsUiHandlers> {
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

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

package stroom.search.elastic.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.presenter.CredentialClient;
import stroom.credentials.client.presenter.CredentialListModel;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.http.client.component.HttpClientConfigEditorController;
import stroom.item.client.SelectionBox;
import stroom.search.elastic.client.presenter.ElasticClusterSettingsPresenter.ElasticClusterSettingsView;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticClusterResource;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.http.HttpClientConfig;
import stroom.widget.component.client.Contexts;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.View;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ElasticClusterSettingsPresenter
        extends DocPresenter<ElasticClusterSettingsView, ElasticClusterDoc>
        implements ElasticClusterSettingsUiHandlers {

    private static final ElasticClusterResource ELASTIC_CLUSTER_RESOURCE = GWT.create(ElasticClusterResource.class);

    private final RestFactory restFactory;
    private final CredentialClient credentialClient;
    private final Provider<HttpClientConfigEditorController> httpClientConfigProvider;
    private HttpClientConfig httpClientConfiguration;

    @Inject
    public ElasticClusterSettingsPresenter(
            final EventBus eventBus,
            final ElasticClusterSettingsView view,
            final RestFactory restFactory,
            final CredentialClient credentialClient,
            final Provider<HttpClientConfigEditorController> httpClientConfigProvider) {
        super(eventBus, view);
        // Read only as its document is
        watch(Contexts.READ_ONLY, getView()::onReadOnly);

        this.restFactory = restFactory;
        this.credentialClient = credentialClient;
        this.httpClientConfigProvider = httpClientConfigProvider;

        final CredentialListModel credentialListModel = new CredentialListModel(
                eventBus,
                credentialClient,
                Set.of(CredentialType.ACCESS_TOKEN));
        credentialListModel.setTaskMonitorFactory(this);
        view.getApiKeySelectionBox().setModel(credentialListModel);

        view.setUiHandlers(this);

    }

    @Override
    public void onTestConnection(final TaskMonitorFactory taskMonitorFactory) {
        // Send the real document identity, as the server checks the user can edit this cluster before
        // testing it with the stored credentials.
        final ElasticClusterDoc cluster = onWrite(getEntity());
        restFactory
                .create(ELASTIC_CLUSTER_RESOURCE)
                .method(res -> res.testCluster(cluster))
                .onSuccess(result -> {
                    if (result.isOk()) {
                        AlertEvent.fireInfo(this, "Connection Success", result.getMessage(), null);
                    } else {
                        AlertEvent.fireError(this, "Connection Failure", result.getMessage(), null);
                    }
                })
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void onSetHttpClientConfiguration() {
        // Read only as this document is, through its context
        httpClientConfigProvider.get().show(this, httpClientConfiguration, updated -> {
            if (!Objects.equals(httpClientConfiguration, updated)) {
                httpClientConfiguration = updated;
                onChange();
            }
        });
    }

    @Override
    protected void onRead(final DocRef docRef, final ElasticClusterDoc cluster) {
        final ElasticConnectionConfig connectionConfig = cluster.getConnection() != null
                ? cluster.getConnection()
                : new ElasticConnectionConfig();

        getView().setConnectionUrls(NullSafe.list(connectionConfig.getConnectionUrls()));

        if (connectionConfig.getApiKeyName() != null) {
            credentialClient.getCredentialByName(connectionConfig.getApiKeyName(), credential ->
                    getView().getApiKeySelectionBox().setValue(credential), this);
        } else {
            getView().getApiKeySelectionBox().setValue(null);
        }

        httpClientConfiguration = connectionConfig.getHttpClientConfiguration();
        if (httpClientConfiguration == null) {
            restFactory
                    .create(ELASTIC_CLUSTER_RESOURCE)
                    .method(ElasticClusterResource::getDefaultHttpClientConfig)
                    .onSuccess(result -> httpClientConfiguration = result)
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    @Override
    protected ElasticClusterDoc onWrite(final ElasticClusterDoc cluster) {
        final ElasticConnectionConfig connectionConfig = new ElasticConnectionConfig();
        connectionConfig.setConnectionUrls(getView().getConnectionUrls());
        connectionConfig.setApiKeyName(NullSafe.get(
                getView().getApiKeySelectionBox().getValue(),
                Credential::getName));
        connectionConfig.setHttpClientConfiguration(httpClientConfiguration);

        return cluster.copy().connection(connectionConfig).build();
    }

    public interface ElasticClusterSettingsView
            extends View, HasUiHandlers<ElasticClusterSettingsUiHandlers> {

        /// Shows whether the user may change what this shows.
        ///
        /// @param readOnly Whether it may not be changed.
        void onReadOnly(boolean readOnly);

        List<String> getConnectionUrls();

        void setConnectionUrls(List<String> connectionUrls);

        SelectionBox<Credential> getApiKeySelectionBox();
    }
}

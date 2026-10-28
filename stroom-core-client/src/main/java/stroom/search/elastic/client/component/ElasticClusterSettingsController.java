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

package stroom.search.elastic.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.CredentialClient;
import stroom.credentials.client.CredentialListModel;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.http.client.component.HttpClientConfigEditorController;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticClusterResource;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.http.HttpClientConfig;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.Set;

/// An Elasticsearch cluster's Settings tab ([ElasticClusterSettings]): its node URLs, API key and
/// HTTP client configuration, and a test of the connection with them. Read only as its document is.
public class ElasticClusterSettingsController extends Controller {

    private static final ElasticClusterResource ELASTIC_CLUSTER_RESOURCE = GWT.create(ElasticClusterResource.class);

    private final ElasticClusterSettings view;
    private final RestFactory restFactory;
    private final CredentialClient credentialClient;
    private final Provider<HttpClientConfigEditorController> httpClientConfigProvider;
    private ElasticClusterDoc cluster;
    private HttpClientConfig httpClientConfiguration;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus                 The application's event bus.
    /// @param view                     The tab.
    /// @param restFactory              Tests the connection and fetches the default HTTP client
    ///                                 configuration.
    /// @param credentialClient         Finds the API key credentials.
    /// @param httpClientConfigProvider Makes the dialog that edits the HTTP client configuration.
    @Inject
    public ElasticClusterSettingsController(
            final EventBus eventBus,
            final ElasticClusterSettings view,
            final RestFactory restFactory,
            final CredentialClient credentialClient,
            final Provider<HttpClientConfigEditorController> httpClientConfigProvider) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.credentialClient = credentialClient;
        this.httpClientConfigProvider = httpClientConfigProvider;
        initWidget(view);

        final CredentialListModel credentialListModel = new CredentialListModel(
                eventBus,
                credentialClient,
                Set.of(CredentialType.ACCESS_TOKEN));
        credentialListModel.setTaskMonitorFactory(taskMonitor());
        view.setApiKeyModel(credentialListModel);

        view.setOnChange(this::markChanged);
        view.setOnSetHttpClientConfig(this::onSetHttpClientConfiguration);
        view.setOnTestConnection(this::onTestConnection);
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    private void onTestConnection() {
        // Send the real document identity, as the server checks the user can edit this cluster before
        // testing it with the stored credentials.
        final ElasticClusterDoc cluster = write(this.cluster);
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
                .owner(this)
                .taskMonitorFactory(view.getTestConnectionTaskMonitor())
                .exec();
    }

    private void onSetHttpClientConfiguration() {
        // Read only as this document is, through its context
        httpClientConfigProvider.get().show(this, httpClientConfiguration, updated -> {
            if (!Objects.equals(httpClientConfiguration, updated)) {
                httpClientConfiguration = updated;
                markChanged();
            }
        });
    }

    /// Shows a cluster's settings.
    ///
    /// @param docRef  The cluster.
    /// @param cluster The cluster.
    public void read(final DocRef docRef, final ElasticClusterDoc cluster) {
        this.cluster = cluster;
        final ElasticConnectionConfig connectionConfig = cluster.getConnection() != null
                ? cluster.getConnection()
                : new ElasticConnectionConfig();

        view.setConnectionUrls(NullSafe.list(connectionConfig.getConnectionUrls()));

        if (connectionConfig.getApiKeyName() != null) {
            credentialClient.getCredentialByName(connectionConfig.getApiKeyName(), guard(view::setApiKey),
                    taskMonitor());
        } else {
            view.setApiKey(null);
        }

        httpClientConfiguration = connectionConfig.getHttpClientConfiguration();
        if (httpClientConfiguration == null) {
            restFactory
                    .create(ELASTIC_CLUSTER_RESOURCE)
                    .method(ElasticClusterResource::getDefaultHttpClientConfig)
                    .onSuccess(result -> httpClientConfiguration = result)
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }

    /// Writes the settings shown into a copy of a cluster.
    ///
    /// @param cluster The cluster.
    /// @return A copy of it with the settings shown.
    public ElasticClusterDoc write(final ElasticClusterDoc cluster) {
        final ElasticConnectionConfig connectionConfig = new ElasticConnectionConfig();
        connectionConfig.setConnectionUrls(view.getConnectionUrls());
        connectionConfig.setApiKeyName(NullSafe.get(view.getApiKey(), Credential::getName));
        connectionConfig.setHttpClientConfiguration(httpClientConfiguration);

        return cluster.copy().connection(connectionConfig).build();
    }

}

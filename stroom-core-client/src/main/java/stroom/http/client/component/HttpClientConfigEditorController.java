/*
 * Copyright 2025 Crown Copyright
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

package stroom.http.client.component;

import stroom.util.shared.http.HttpClientConfig;
import stroom.util.shared.http.HttpTlsConfig;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Edits an HTTP client's settings ([HttpClientConfigEditor]) in a dialog made for one use, its TLS
/// settings in another, opened from it.
public class HttpClientConfigEditorController extends Controller {

    private final HttpClientConfigEditor view;
    private final Provider<HttpTlsConfigEditorController> httpTlsConfigProvider;
    private HttpTlsConfig httpTlsConfig;

    /// Made by GIN, with its form.
    ///
    /// @param eventBus              The application's event bus.
    /// @param view                  The form.
    /// @param httpTlsConfigProvider Makes the TLS settings' dialog.
    @Inject
    public HttpClientConfigEditorController(final EventBus eventBus,
                                            final HttpClientConfigEditor view,
                                            final Provider<HttpTlsConfigEditorController> httpTlsConfigProvider) {
        super(eventBus);
        this.view = view;
        this.httpTlsConfigProvider = httpTlsConfigProvider;
        initWidget(view);
        // Read only as its document is
        watch(Contexts.READ_ONLY, view::setReadOnly);
        view.setOnSetHttpTlsConfig(this::onSetHttpTlsConfig);
    }

    /// Shows an HTTP client's settings in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener           What shows it.
    /// @param httpClientConfig The settings, or `null` for none yet.
    /// @param consumer         Given the edited settings on OK, or `httpClientConfig` on Cancel.
    public void show(final HasControllerNode opener,
                     final HttpClientConfig httpClientConfig,
                     final Consumer<HttpClientConfig> consumer) {
        read(httpClientConfig);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .caption("Edit HTTP Client Configuration")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        consumer.accept(write());
                    } else {
                        consumer.accept(httpClientConfig);
                    }
                    e.hide();
                })
                .fire();
    }

    private void onSetHttpTlsConfig() {
        httpTlsConfigProvider.get().show(this, httpTlsConfig, updated -> httpTlsConfig = updated);
    }

    private void read(final HttpClientConfig config) {
        if (config != null) {
            view.setTimeout(config.getTimeout());
            view.setConnectionTimeout(config.getConnectionTimeout());
            view.setConnectionRequestTimeout(config.getConnectionRequestTimeout());
            view.setTimeToLive(config.getTimeToLive());
            view.setCookiesEnabled(config.isCookiesEnabled());
            view.setFollowRedirects(config.isFollowRedirects());
            view.setMaxConnections(config.getMaxConnections());
            view.setMaxConnectionsPerRoute(config.getMaxConnectionsPerRoute());
            view.setKeepAlive(config.getKeepAlive());
            view.setRetries(config.getRetries());
            view.setUserAgent(config.getUserAgent());
            view.setValidateAfterInactivityPeriod(config.getValidateAfterInactivityPeriod());
            httpTlsConfig = config.getTls();
        }
    }

    private HttpClientConfig write() {
        return HttpClientConfig
                .builder()
                .timeout(view.getTimeout())
                .connectionTimeout(view.getConnectionTimeout())
                .connectionRequestTimeout(view.getConnectionRequestTimeout())
                .timeToLive(view.getTimeToLive())
                .cookiesEnabled(view.isCookiesEnabled())
                .followRedirects(view.isFollowRedirects())
                .maxConnections(view.getMaxConnections())
                .maxConnectionsPerRoute(view.getMaxConnectionsPerRoute())
                .keepAlive(view.getKeepAlive())
                .retries(view.getRetries())
                .userAgent(view.getUserAgent())
                .validateAfterInactivityPeriod(view.getValidateAfterInactivityPeriod())
                .tlsConfiguration(httpTlsConfig)
                .build();
    }
}

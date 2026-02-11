/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.http.client.presenter;

import stroom.http.client.presenter.HttpClientConfigPresenter.HttpClientConfigView;
import stroom.util.shared.http.HttpClientConfig;
import stroom.util.shared.http.HttpTlsConfig;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;
import java.util.function.Consumer;

public class HttpClientConfigPresenter
        extends MyPresenterWidget<HttpClientConfigView>
        implements HttpClientConfigUiHandlers {

    private final Provider<HttpTlsConfigPresenter> httpTlsConfigPresenterProvider;
    private HttpTlsConfig httpTlsConfig;

    @Inject
    public HttpClientConfigPresenter(
            final EventBus eventBus,
            final HttpClientConfigView view,
            final Provider<HttpTlsConfigPresenter> httpTlsConfigPresenterProvider) {
        super(eventBus, view);
        this.httpTlsConfigPresenterProvider = httpTlsConfigPresenterProvider;
        view.setUiHandlers(this);
    }

    public void show(final HttpClientConfig httpClientConfig,
                     final Consumer<HttpClientConfig> consumer) {
        read(httpClientConfig);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Edit HTTP Client Configuration")
                .onShow(e -> getView().focus())
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

    @Override
    public void onDirty() {
    }

    @Override
    public void onSetHttpTlsConfig() {
        final HttpTlsConfigPresenter httpTlsConfigPresenter = httpTlsConfigPresenterProvider.get();
        httpTlsConfigPresenter.show(httpTlsConfig, updated -> {
            if (!Objects.equals(httpTlsConfig, updated)) {
                httpTlsConfig = updated;
            }
        });
    }

    public void read(final HttpClientConfig config) {
        if (config != null) {
            getView().setTimeout(config.getTimeout());
            getView().setConnectionTimeout(config.getConnectionTimeout());
            getView().setConnectionRequestTimeout(config.getConnectionRequestTimeout());
            getView().setTimeToLive(config.getTimeToLive());
            getView().setCookiesEnabled(config.isCookiesEnabled());
            getView().setMaxConnections(config.getMaxConnections());
            getView().setMaxConnectionsPerRoute(config.getMaxConnectionsPerRoute());
            getView().setKeepAlive(config.getKeepAlive());
            getView().setRetries(config.getRetries());
            getView().setValidateAfterInactivityPeriod(config.getValidateAfterInactivityPeriod());
            httpTlsConfig = config.getTls();
        }
    }

    public HttpClientConfig write() {
        return HttpClientConfig
                .builder()
                .timeout(getView().getTimeout())
                .connectionTimeout(getView().getConnectionTimeout())
                .connectionRequestTimeout(getView().getConnectionRequestTimeout())
                .timeToLive(getView().getTimeToLive())
                .cookiesEnabled(getView().isCookiesEnabled())
                .maxConnections(getView().getMaxConnections())
                .maxConnectionsPerRoute(getView().getMaxConnectionsPerRoute())
                .keepAlive(getView().getKeepAlive())
                .retries(getView().getRetries())
                .validateAfterInactivityPeriod(getView().getValidateAfterInactivityPeriod())
                .tlsConfiguration(httpTlsConfig)
                .build();
    }

    public interface HttpClientConfigView
            extends View, Focus, HasUiHandlers<HttpClientConfigUiHandlers> {

        void setTimeout(SimpleDuration timeout);

        SimpleDuration getTimeout();

        void setConnectionTimeout(SimpleDuration connectionTimeout);

        SimpleDuration getConnectionTimeout();

        void setConnectionRequestTimeout(SimpleDuration connectionRequestTimeout);

        SimpleDuration getConnectionRequestTimeout();

        void setTimeToLive(SimpleDuration timeToLive);

        SimpleDuration getTimeToLive();

        void setCookiesEnabled(boolean cookiesEnabled);

        boolean isCookiesEnabled();

        void setMaxConnections(int maxConnections);

        int getMaxConnections();

        void setMaxConnectionsPerRoute(int maxConnectionsPerRoute);

        int getMaxConnectionsPerRoute();

        void setKeepAlive(SimpleDuration keepAlive);

        SimpleDuration getKeepAlive();

        void setRetries(int retries);

        int getRetries();


//    // Changed this to be a string rather than an optional to avoid serialisation issues when
//    // we merge our config.yml node tree with a default node tree and then serialise for drop wiz to
//    // read.
//    private final String userAgent;

//    private final HttpProxyConfiguration proxyConfiguration;

        void setValidateAfterInactivityPeriod(SimpleDuration validateAfterInactivityPeriod);

        SimpleDuration getValidateAfterInactivityPeriod();
    }
}

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

package stroom.http.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.http.client.presenter.HttpClientConfigPresenter.HttpClientConfigView;
import stroom.util.shared.http.HttpClientConfig;
import stroom.util.shared.http.HttpHeader;
import stroom.util.shared.http.HttpProxyConfig;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.function.Consumer;

public class HttpClientConfigPresenter
        extends MyPresenterWidget<HttpClientConfigView> {

    // Shown on the dialog's TLS tab.
    private final HttpTlsConfigPresenter httpTlsConfigPresenter;
    // Not edited here, so kept as it was read rather than lost.
    private HttpProxyConfig httpProxyConfig;
    private boolean readOnly;

    @Inject
    public HttpClientConfigPresenter(
            final EventBus eventBus,
            final HttpClientConfigView view,
            final HttpTlsConfigPresenter httpTlsConfigPresenter) {
        super(eventBus, view);
        this.httpTlsConfigPresenter = httpTlsConfigPresenter;
        view.setTlsView(httpTlsConfigPresenter.getView());
    }

    public void show(final HttpClientConfig httpClientConfig,
                     final boolean readOnly,
                     final Consumer<HttpClientConfig> consumer) {
        this.readOnly = readOnly;
        read(httpClientConfig);
        getView().onReadOnly(readOnly);
        httpTlsConfigPresenter.setReadOnly(readOnly);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(600, 600))
                .caption("Edit HTTP Client Configuration")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final String duplicate = HttpHeader.findDuplicateName(getView().getHeaders());
                        if (duplicate != null) {
                            getView().showHeadersTab();
                            AlertEvent.fireError(this,
                                    "The header '" + duplicate + "' is given more than once. Header names are "
                                    + "not case-sensitive, so each can only be given once. Remove or rename "
                                    + "the duplicate.",
                                    e::reset);
                            return;
                        }
                        consumer.accept(write());
                    } else {
                        consumer.accept(httpClientConfig);
                    }
                    e.hide();
                })
                .fire();
    }

    public void read(final HttpClientConfig config) {
        if (config != null) {
            getView().setTimeout(config.getTimeout());
            getView().setConnectionTimeout(config.getConnectionTimeout());
            getView().setConnectionRequestTimeout(config.getConnectionRequestTimeout());
            getView().setTimeToLive(config.getTimeToLive());
            getView().setCookiesEnabled(config.isCookiesEnabled());
            getView().setFollowRedirects(config.isFollowRedirects());
            getView().setMaxConnections(config.getMaxConnections());
            getView().setMaxConnectionsPerRoute(config.getMaxConnectionsPerRoute());
            getView().setKeepAlive(config.getKeepAlive());
            getView().setRetries(config.getRetries());
            getView().setValidateAfterInactivityPeriod(config.getValidateAfterInactivityPeriod());
            getView().setUserAgent(config.getUserAgent());
            getView().setHeaders(config.getHeaders());
            httpTlsConfigPresenter.read(config.getTls());
            httpProxyConfig = config.getProxy();
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
                .followRedirects(getView().isFollowRedirects())
                .maxConnections(getView().getMaxConnections())
                .maxConnectionsPerRoute(getView().getMaxConnectionsPerRoute())
                .keepAlive(getView().getKeepAlive())
                .retries(getView().getRetries())
                .validateAfterInactivityPeriod(getView().getValidateAfterInactivityPeriod())
                .userAgent(getView().getUserAgent())
                .headers(getView().getHeaders())
                .proxyConfig(httpProxyConfig)
                .tlsConfiguration(httpTlsConfigPresenter.write())
                .build();
    }

    public interface HttpClientConfigView
            extends View, Focus, ReadOnlyChangeHandler {

        /// Shows the Headers tab, e.g. to point out a problem with the headers.
        void showHeadersTab();

        /// @param tlsView The TLS settings, shown on their own tab.
        void setTlsView(View tlsView);

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

        void setFollowRedirects(boolean followRedirects);

        boolean isFollowRedirects();

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

        /// @param userAgent The User-Agent header to send, or null for the default.
        void setUserAgent(String userAgent);

        /// @return The User-Agent header to send, or null for the default.
        String getUserAgent();

        /// @param headers The headers to send with every request, or null for none.
        void setHeaders(List<HttpHeader> headers);

        /// @return The headers to send with every request, or null for none.
        List<HttpHeader> getHeaders();
    }
}

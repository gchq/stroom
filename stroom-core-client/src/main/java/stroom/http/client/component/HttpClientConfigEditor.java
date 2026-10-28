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

import stroom.util.shared.NullSafe;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.customdatebox.client.DurationPicker;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;

/// An HTTP client's settings: its timeouts, pool sizes, retries and user agent, with a button that
/// opens its TLS settings.
public class HttpClientConfigEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DurationPicker timeout;
    @UiField
    DurationPicker connectionTimeout;
    @UiField
    DurationPicker connectionRequestTimeout;
    @UiField
    DurationPicker timeToLive;
    @UiField
    TickBox cookiesEnabled;
    @UiField
    TickBox followRedirects;
    @UiField
    ValueSpinner maxConnections;
    @UiField
    ValueSpinner maxConnectionsPerRoute;
    @UiField
    DurationPicker keepAlive;
    @UiField
    ValueSpinner retries;
    @UiField
    TextBox userAgent;
    @UiField
    DurationPicker validateAfterInactivityPeriod;
    @UiField
    Button setHttpTlsConfig;

    private Runnable onSetHttpTlsConfig = () -> {
    };

    /// Makes an empty form.
    public HttpClientConfigEditor() {
        initWidget(BINDER.createAndBindUi(this));
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        timeout.setReadOnly(readOnly);
        connectionTimeout.setReadOnly(readOnly);
        connectionRequestTimeout.setReadOnly(readOnly);
        timeToLive.setReadOnly(readOnly);
        cookiesEnabled.setReadOnly(readOnly);
        followRedirects.setReadOnly(readOnly);
        maxConnections.setReadOnly(readOnly);
        maxConnectionsPerRoute.setReadOnly(readOnly);
        keepAlive.setReadOnly(readOnly);
        retries.setReadOnly(readOnly);
        userAgent.setReadOnly(readOnly);
        validateAfterInactivityPeriod.setReadOnly(readOnly);
    }

    /// Puts the focus on the timeout.
    public void focus() {
        timeout.focus();
    }

    /// @param timeout How long to wait for a response.
    public void setTimeout(final SimpleDuration timeout) {
        this.timeout.setValue(timeout);
    }

    /// @return How long to wait for a response.
    public SimpleDuration getTimeout() {
        return timeout.getValue();
    }

    /// @param connectionTimeout How long to wait for a connection.
    public void setConnectionTimeout(final SimpleDuration connectionTimeout) {
        this.connectionTimeout.setValue(connectionTimeout);
    }

    /// @return How long to wait for a connection.
    public SimpleDuration getConnectionTimeout() {
        return connectionTimeout.getValue();
    }

    /// @param connectionRequestTimeout How long to wait for a pooled connection.
    public void setConnectionRequestTimeout(final SimpleDuration connectionRequestTimeout) {
        this.connectionRequestTimeout.setValue(connectionRequestTimeout);
    }

    /// @return How long to wait for a pooled connection.
    public SimpleDuration getConnectionRequestTimeout() {
        return connectionRequestTimeout.getValue();
    }

    /// @param timeToLive How long a pooled connection may stay idle.
    public void setTimeToLive(final SimpleDuration timeToLive) {
        this.timeToLive.setValue(timeToLive);
    }

    /// @return How long a pooled connection may stay idle.
    public SimpleDuration getTimeToLive() {
        return timeToLive.getValue();
    }

    /// @param cookiesEnabled Whether cookies are kept and sent back.
    public void setCookiesEnabled(final boolean cookiesEnabled) {
        this.cookiesEnabled.setValue(cookiesEnabled);
    }

    /// @return Whether cookies are kept and sent back.
    public boolean isCookiesEnabled() {
        return cookiesEnabled.getValue();
    }

    /// @param followRedirects Whether redirects are followed.
    public void setFollowRedirects(final boolean followRedirects) {
        this.followRedirects.setValue(followRedirects);
    }

    /// @return Whether redirects are followed.
    public boolean isFollowRedirects() {
        return followRedirects.getValue();
    }

    /// @param maxConnections The most pooled connections across all hosts.
    public void setMaxConnections(final int maxConnections) {
        this.maxConnections.setValue(maxConnections);
    }

    /// @return The most pooled connections across all hosts.
    public int getMaxConnections() {
        return maxConnections.getIntValue();
    }

    /// @param maxConnectionsPerRoute The most pooled connections to one host.
    public void setMaxConnectionsPerRoute(final int maxConnectionsPerRoute) {
        this.maxConnectionsPerRoute.setValue(maxConnectionsPerRoute);
    }

    /// @return The most pooled connections to one host.
    public int getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute.getIntValue();
    }

    /// @param keepAlive How long a connection is kept open for reuse.
    public void setKeepAlive(final SimpleDuration keepAlive) {
        this.keepAlive.setValue(keepAlive);
    }

    /// @return How long a connection is kept open for reuse.
    public SimpleDuration getKeepAlive() {
        return keepAlive.getValue();
    }

    /// @param retries How many times a failed request is retried.
    public void setRetries(final int retries) {
        this.retries.setValue(retries);
    }

    /// @return How many times a failed request is retried.
    public int getRetries() {
        return retries.getIntValue();
    }

    /// @param userAgent The User-Agent header value, or null for the HTTP client's own.
    public void setUserAgent(final String userAgent) {
        this.userAgent.setValue(NullSafe.string(userAgent));
    }

    /// @return The User-Agent header value, or null (when blank) for the HTTP client's own.
    public String getUserAgent() {
        return NullSafe.isBlankString(userAgent.getValue())
                ? null
                : userAgent.getValue().trim();
    }

    /// @param validateAfterInactivityPeriod How long a pooled connection may be idle before it
    ///                                      is checked.
    public void setValidateAfterInactivityPeriod(final SimpleDuration validateAfterInactivityPeriod) {
        this.validateAfterInactivityPeriod.setValue(validateAfterInactivityPeriod);
    }

    /// @return How long a pooled connection may be idle before it is checked.
    public SimpleDuration getValidateAfterInactivityPeriod() {
        return validateAfterInactivityPeriod.getValue();
    }

    /// @param onSetHttpTlsConfig Called when Set HTTP TLS Config is pressed.
    public void setOnSetHttpTlsConfig(final Runnable onSetHttpTlsConfig) {
        this.onSetHttpTlsConfig = onSetHttpTlsConfig;
    }

    @UiHandler("setHttpTlsConfig")
    void onSetHttpTlsConfig(final ClickEvent event) {
        onSetHttpTlsConfig.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, HttpClientConfigEditor> {

    }
}

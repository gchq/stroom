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

package stroom.http.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.http.client.presenter.HttpClientConfigPresenter.HttpClientConfigView;
import stroom.http.client.presenter.HttpClientConfigUiHandlers;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.button.client.Button;
import stroom.widget.customdatebox.client.DurationPicker;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class HttpClientConfigViewImpl
        extends ViewWithUiHandlers<HttpClientConfigUiHandlers>
        implements HttpClientConfigView, ReadOnlyChangeHandler {

    private final Widget widget;

    @UiField
    DurationPicker timeout;
    @UiField
    DurationPicker connectionTimeout;
    @UiField
    DurationPicker connectionRequestTimeout;
    @UiField
    DurationPicker timeToLive;
    @UiField
    CustomCheckBox cookiesEnabled;
    @UiField
    ValueSpinner maxConnections;
    @UiField
    ValueSpinner maxConnectionsPerRoute;
    @UiField
    DurationPicker keepAlive;
    @UiField
    ValueSpinner retries;
    @UiField
    DurationPicker validateAfterInactivityPeriod;
    @UiField
    Button setHttpTlsConfig;

    @Inject
    public HttpClientConfigViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        maxConnections.setMin(1);
        maxConnections.setMax(1000000);
        maxConnectionsPerRoute.setMin(1);
        maxConnectionsPerRoute.setMax(1000000);
        retries.setMax(1000);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        timeout.setEnabled(!readOnly);
        connectionTimeout.setEnabled(!readOnly);
        connectionRequestTimeout.setEnabled(!readOnly);
        timeToLive.setEnabled(!readOnly);
        cookiesEnabled.setEnabled(!readOnly);
        maxConnections.setEnabled(!readOnly);
        maxConnectionsPerRoute.setEnabled(!readOnly);
        keepAlive.setEnabled(!readOnly);
        retries.setEnabled(!readOnly);
        validateAfterInactivityPeriod.setEnabled(!readOnly);
    }

    @Override
    public void focus() {
        timeout.focus();
    }

    @Override
    public void setTimeout(final SimpleDuration timeout) {
        this.timeout.setValue(timeout);
    }

    @Override
    public SimpleDuration getTimeout() {
        return timeout.getValue();
    }

    @Override
    public void setConnectionTimeout(final SimpleDuration connectionTimeout) {
        this.connectionTimeout.setValue(connectionTimeout);
    }

    @Override
    public SimpleDuration getConnectionTimeout() {
        return connectionTimeout.getValue();
    }

    @Override
    public void setConnectionRequestTimeout(final SimpleDuration connectionRequestTimeout) {
        this.connectionRequestTimeout.setValue(connectionRequestTimeout);
    }

    @Override
    public SimpleDuration getConnectionRequestTimeout() {
        return connectionRequestTimeout.getValue();
    }

    @Override
    public void setTimeToLive(final SimpleDuration timeToLive) {
        this.timeToLive.setValue(timeToLive);
    }

    @Override
    public SimpleDuration getTimeToLive() {
        return timeToLive.getValue();
    }

    @Override
    public void setCookiesEnabled(final boolean cookiesEnabled) {
        this.cookiesEnabled.setValue(cookiesEnabled);
    }

    @Override
    public boolean isCookiesEnabled() {
        return cookiesEnabled.getValue();
    }

    @Override
    public void setMaxConnections(final int maxConnections) {
        this.maxConnections.setValue(maxConnections);
    }

    @Override
    public int getMaxConnections() {
        return maxConnections.getIntValue();
    }

    @Override
    public void setMaxConnectionsPerRoute(final int maxConnectionsPerRoute) {
        this.maxConnectionsPerRoute.setValue(maxConnectionsPerRoute);
    }

    @Override
    public int getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute.getIntValue();
    }

    @Override
    public void setKeepAlive(final SimpleDuration keepAlive) {
        this.keepAlive.setValue(keepAlive);
    }

    @Override
    public SimpleDuration getKeepAlive() {
        return keepAlive.getValue();
    }

    @Override
    public void setRetries(final int retries) {
        this.retries.setValue(retries);
    }

    @Override
    public int getRetries() {
        return retries.getIntValue();
    }

    @Override
    public void setValidateAfterInactivityPeriod(final SimpleDuration validateAfterInactivityPeriod) {
        this.validateAfterInactivityPeriod.setValue(validateAfterInactivityPeriod);
    }

    @Override
    public SimpleDuration getValidateAfterInactivityPeriod() {
        return validateAfterInactivityPeriod.getValue();
    }

    @UiHandler({
            "timeout",
            "connectionTimeout",
            "connectionRequestTimeout",
            "timeToLive",
            "cookiesEnabled",
            "maxConnections",
            "maxConnectionsPerRoute",
            "keepAlive",
            "retries",
            "validateAfterInactivityPeriod"})
    public void onChange(final ValueChangeEvent<?> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDirty();
        }
    }

    @UiHandler("setHttpTlsConfig")
    public void onSetHttpTlsConfig(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onSetHttpTlsConfig();
        }
    }

    public interface Binder extends UiBinder<Widget, HttpClientConfigViewImpl> {

    }
}

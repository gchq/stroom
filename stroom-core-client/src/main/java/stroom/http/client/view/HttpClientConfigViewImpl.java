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

package stroom.http.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.http.client.presenter.HttpClientConfigPresenter.HttpClientConfigView;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.http.HttpHeader;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.customdatebox.client.DurationPicker;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.tab.client.view.LinkTabBar;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpClientConfigViewImpl
        extends ViewImpl
        implements HttpClientConfigView, ReadOnlyChangeHandler {

    private final Widget widget;
    // Each tab and the panel it shows, in order.
    private final Map<TabData, Panel> tabs = new LinkedHashMap<>();
    private final TabData headersTabData;
    private final FlowPanel headerRows = new FlowPanel();
    private final InlineSvgButton addHeader = new InlineSvgButton();
    private boolean readOnly;

    @UiField
    LinkTabBar tabBar;
    @UiField
    FlowPanel generalTab;
    @UiField
    SimplePanel tlsTab;
    @UiField
    FlowPanel headersTab;
    @UiField
    FlowPanel timeoutsTab;
    @UiField
    FlowPanel limitsTab;
    @UiField
    FlowPanel headers;
    @UiField
    TextBox userAgent;
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
    CustomCheckBox followRedirects;
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

    @Inject
    public HttpClientConfigViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        maxConnections.setMin(1);
        maxConnections.setMax(1000000);
        maxConnectionsPerRoute.setMin(1);
        maxConnectionsPerRoute.setMax(1000000);
        retries.setMax(1000);

        addTab("General", generalTab);
        addTab("TLS", tlsTab);
        headersTabData = addTab("Headers", headersTab);
        addTab("Timeouts", timeoutsTab);
        addTab("Limits", limitsTab);
        tabBar.addSelectionHandler(event -> selectTab(event.getSelectedItem()));
        selectTab(tabs.keySet().iterator().next());

        addHeader.setSvg(SvgImage.ADD);
        addHeader.setTitle("Add Header");
        addHeader.addStyleName("allow-focus");
        addHeader.addClickHandler(event -> addHeaderRow("", "").focus());
        headers.add(headerRows);
        headers.add(addHeader);
    }

    private TabData addTab(final String text, final Panel panel) {
        final TabData tab = new TabDataImpl(text, false);
        tabs.put(tab, panel);
        tabBar.addTab(tab);
        return tab;
    }

    private void selectTab(final TabData selected) {
        if (selected != null && tabs.containsKey(selected)) {
            tabBar.selectTab(selected);
            tabs.forEach((tab, panel) -> panel.setVisible(tab == selected));
        }
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        userAgent.setEnabled(!readOnly);
        timeout.setEnabled(!readOnly);
        connectionTimeout.setEnabled(!readOnly);
        connectionRequestTimeout.setEnabled(!readOnly);
        timeToLive.setEnabled(!readOnly);
        cookiesEnabled.setEnabled(!readOnly);
        followRedirects.setEnabled(!readOnly);
        maxConnections.setEnabled(!readOnly);
        maxConnectionsPerRoute.setEnabled(!readOnly);
        keepAlive.setEnabled(!readOnly);
        retries.setEnabled(!readOnly);
        validateAfterInactivityPeriod.setEnabled(!readOnly);
        addHeader.setEnabled(!readOnly);
        for (int i = 0; i < headerRows.getWidgetCount(); i++) {
            ((HeaderRow) headerRows.getWidget(i)).setEnabled(!readOnly);
        }
    }

    @Override
    public void focus() {
        followRedirects.focus();
    }

    @Override
    public void setUserAgent(final String userAgent) {
        this.userAgent.setText(userAgent);
    }

    @Override
    public String getUserAgent() {
        final String text = userAgent.getText().trim();
        return text.isEmpty()
                ? null
                : text;
    }

    @Override
    public void setHeaders(final List<HttpHeader> headers) {
        headerRows.clear();
        if (headers != null) {
            for (final HttpHeader header : headers) {
                addHeaderRow(header.getName(), header.getValue());
            }
        }
    }

    @Override
    public List<HttpHeader> getHeaders() {
        final List<HttpHeader> list = new ArrayList<>();
        for (int i = 0; i < headerRows.getWidgetCount(); i++) {
            final HeaderRow row = (HeaderRow) headerRows.getWidget(i);
            final String name = row.name.getText().trim();
            if (!name.isEmpty()) {
                list.add(new HttpHeader(name, row.value.getText()));
            }
        }
        return list.isEmpty()
                ? null
                : list;
    }

    private HeaderRow addHeaderRow(final String name, final String value) {
        final HeaderRow row = new HeaderRow(name, value);
        row.setEnabled(!readOnly);
        headerRows.add(row);
        return row;
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
    public void setFollowRedirects(final boolean followRedirects) {
        this.followRedirects.setValue(followRedirects);
    }

    @Override
    public boolean isFollowRedirects() {
        return followRedirects.getValue();
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

    @Override
    public void showHeadersTab() {
        selectTab(headersTabData);
    }

    @Override
    public void setTlsView(final View tlsView) {
        tlsTab.setWidget(tlsView.asWidget());
    }

    public interface Binder extends UiBinder<Widget, HttpClientConfigViewImpl> {

    }

    // A header's name and value, with a button to remove it.
    private static final class HeaderRow extends FlowPanel {

        private final TextBox name = new TextBox();
        private final TextBox value = new TextBox();
        private final InlineSvgButton remove = new InlineSvgButton();

        private HeaderRow(final String headerName, final String headerValue) {
            setStyleName("httpClientConfig-header");
            name.setText(headerName);
            name.getElement().setAttribute("placeholder", "Header");
            name.setTitle("The header's name, e.g. anthropic-version");
            name.addStyleName("httpClientConfig-headerName allow-focus");
            value.setText(headerValue);
            value.getElement().setAttribute("placeholder", "Value");
            value.setTitle("The header's value, e.g. 2023-06-01");
            value.addStyleName("httpClientConfig-headerValue allow-focus");
            remove.setSvg(SvgImage.DELETE);
            remove.setTitle("Remove Header");
            remove.addStyleName("allow-focus");
            remove.addClickHandler(event -> removeFromParent());
            add(name);
            add(value);
            add(remove);
        }

        private void setEnabled(final boolean enabled) {
            name.setEnabled(enabled);
            value.setEnabled(enabled);
            remove.setEnabled(enabled);
        }

        private void focus() {
            name.setFocus(true);
        }
    }
}

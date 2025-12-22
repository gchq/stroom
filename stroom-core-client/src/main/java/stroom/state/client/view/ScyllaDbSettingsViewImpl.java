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

package stroom.state.client.view;

import stroom.state.client.presenter.ScyllaDbSettingsPresenter.ScyllaDbSettingsView;
import stroom.state.client.presenter.ScyllaDbSettingsUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class ScyllaDbSettingsViewImpl extends ViewWithUiHandlers<ScyllaDbSettingsUiHandlers>
        implements ScyllaDbSettingsView {

    private final Widget widget;

    @UiField
    TextArea connectionConfig;
    @UiField
    TextBox keyspace;
    @UiField
    TextArea keyspaceCql;
    @UiField
    Button testConnection;

    @Inject
    public ScyllaDbSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        testConnection.setIcon(SvgImage.OK);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getConnectionConfig() {
        return connectionConfig.getText();
    }

    @Override
    public void setConnectionConfig(final String connectionConfig) {
        this.connectionConfig.setText(connectionConfig);
    }

    @Override
    public String getKeyspace() {
        return keyspace.getText();
    }

    @Override
    public void setKeyspace(final String keyspace) {
        this.keyspace.setText(keyspace);
    }

    @Override
    public String getKeyspaceCql() {
        return keyspaceCql.getText();
    }

    @Override
    public void setKeyspaceCql(final String keyspaceCql) {
        this.keyspaceCql.setText(keyspaceCql);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        connectionConfig.setEnabled(!readOnly);
        keyspace.setEnabled(!readOnly);
        keyspaceCql.setEnabled(!readOnly);
    }

    @UiHandler("connectionConfig")
    public void onConnectionConfig(final ValueChangeEvent<String> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("keyspace")
    public void onKeyspace(final ValueChangeEvent<String> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("keyspaceCql")
    public void onKeyspaceCql(final ValueChangeEvent<String> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("testConnection")
    public void onTestConnectionClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onTestConnection(testConnection);
        }
    }

    public interface Binder extends UiBinder<Widget, ScyllaDbSettingsViewImpl> {

    }
}

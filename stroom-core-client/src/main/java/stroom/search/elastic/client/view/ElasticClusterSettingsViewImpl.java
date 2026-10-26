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

package stroom.search.elastic.client.view;

import stroom.credentials.shared.Credential;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.item.client.SelectionBox;
import stroom.search.elastic.client.presenter.ElasticClusterSettingsPresenter.ElasticClusterSettingsView;
import stroom.search.elastic.client.presenter.ElasticClusterSettingsUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ElasticClusterSettingsViewImpl extends ViewWithUiHandlers<ElasticClusterSettingsUiHandlers>
        implements ElasticClusterSettingsView, ReadOnlyChangeHandler {

    private final Widget widget;

    @UiField
    TextArea connectionUrls;
    @UiField
    SelectionBox<Credential> apiKey;
    @UiField
    Button setHttpClientConfig;
    @UiField
    Button testConnection;

    @Inject
    public ElasticClusterSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        testConnection.setIcon(SvgImage.OK);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public List<String> getConnectionUrls() {
        return Arrays.stream(connectionUrls.getText().split("\n"))
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    public void setConnectionUrls(final List<String> connectionUrls) {
        this.connectionUrls.setText(String.join("\n", connectionUrls));
    }

    @Override
    public SelectionBox<Credential> getApiKeySelectionBox() {
        return apiKey;
    }

    // Read only: every field can be read and copied but not changed
    @Override
    public void onReadOnly(final boolean readOnly) {
        connectionUrls.setReadOnly(readOnly);
        apiKey.setReadOnly(readOnly);
    }

    private void fireChange() {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    @UiHandler("connectionUrls")
    public void onConnectionUrls(final ValueChangeEvent<String> event) {
        fireChange();
    }

    @UiHandler("apiKey")
    public void onApiKeyValueChange(@SuppressWarnings("unused") final ValueChangeEvent<Credential> event) {
        fireChange();
    }

    @UiHandler("setHttpClientConfig")
    public void onSetHttpClientConfig(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onSetHttpClientConfiguration();
        }
    }

    @UiHandler("testConnection")
    public void onTestConnectionClick(final ClickEvent event) {
        getUiHandlers().onTestConnection(testConnection);
    }

    public interface Binder extends UiBinder<Widget, ElasticClusterSettingsViewImpl> {

    }
}

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

import stroom.credentials.shared.Credential;
import stroom.item.client.SelectionBox;
import stroom.item.client.SelectionListModel;
import stroom.item.client.SimpleSelectionItemWrapper;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextArea;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/// An Elasticsearch cluster's Settings tab: the URLs of its nodes, the API key credential to send
/// it, and buttons to set the HTTP client configuration and to test the connection. Read only, every
/// field can be read and copied but not changed.
public class ElasticClusterSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextArea connectionUrls;
    @UiField
    SelectionBox<Credential> apiKey;
    @UiField
    Button setHttpClientConfig;
    @UiField
    Button testConnection;

    private Runnable onChange = () -> {
    };

    /// Makes the tab, with no URLs or key.
    public ElasticClusterSettings() {
        initWidget(BINDER.createAndBindUi(this));
        connectionUrls.addValueChangeHandler(e -> onChange.run());
        apiKey.addValueChangeHandler(e -> onChange.run());
    }

    /// @return The URLs entered, one per line, without blank lines.
    public List<String> getConnectionUrls() {
        return Arrays.stream(connectionUrls.getText().split("\n"))
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .collect(Collectors.toList());
    }

    /// @param connectionUrls The URLs, shown one per line.
    public void setConnectionUrls(final List<String> connectionUrls) {
        this.connectionUrls.setText(String.join("\n", connectionUrls));
    }

    /// @param model The credentials the API key can be chosen from.
    public void setApiKeyModel(final SelectionListModel<Credential, SimpleSelectionItemWrapper<Credential>> model) {
        apiKey.setModel(model);
    }

    /// @param credential The API key credential, or `null` for none.
    public void setApiKey(final Credential credential) {
        apiKey.setValue(credential);
    }

    /// @return The API key credential chosen, or `null` for none.
    public Credential getApiKey() {
        return apiKey.getValue();
    }

    /// @return The Test Connection button, which spins while the connection is tested.
    public TaskMonitorFactory getTestConnectionTaskMonitor() {
        return testConnection;
    }

    /// @param onChange Run when the user changes the URLs or the key.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param onSetHttpClientConfig Run when Set HTTP Client Config is pressed.
    public void setOnSetHttpClientConfig(final Runnable onSetHttpClientConfig) {
        setHttpClientConfig.setOnClick(onSetHttpClientConfig);
    }

    /// @param onTestConnection Run when Test Connection is pressed.
    public void setOnTestConnection(final Runnable onTestConnection) {
        testConnection.setOnClick(onTestConnection);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        connectionUrls.setReadOnly(readOnly);
        apiKey.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ElasticClusterSettings> {

    }
}

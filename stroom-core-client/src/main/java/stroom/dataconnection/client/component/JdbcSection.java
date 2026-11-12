/*
 * Copyright 2026 Crown Copyright
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

package stroom.dataconnection.client.component;

import stroom.credentials.shared.Credential;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.DescriptionHTML;
import stroom.widget.form.client.TextArea;
import stroom.widget.form.client.TextBox;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.selection.client.SelectionListModel;
import stroom.widget.selection.client.SimpleSelectionItemWrapper;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// The settings of a JDBC Data Connection, in its Settings tab ([DataConnectionSettings]): the
/// database's URL, the credential to connect with, driver properties, the pool and fetch sizes and
/// the shortest automatic dashboard refresh.
public class JdbcSection extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox url;
    @UiField
    DescriptionHTML credentialNote;
    @UiField
    SelectionBox<Credential> credential;
    @UiField
    TextArea connectionProperties;
    @UiField
    ValueSpinner maxPoolSize;
    @UiField
    ValueSpinner fetchSize;
    @UiField
    ValueSpinner minRefreshMinutes;

    private Runnable onChange = () -> {
    };

    /// Makes the section, empty.
    public JdbcSection() {
        initWidget(BINDER.createAndBindUi(this));
        url.addValueChangeHandler(e -> onChange.run());
        connectionProperties.addValueChangeHandler(e -> onChange.run());
        maxPoolSize.addValueChangeHandler(e -> onChange.run());
        fetchSize.addValueChangeHandler(e -> onChange.run());
        minRefreshMinutes.addValueChangeHandler(e -> onChange.run());
        setCredentialNote(null);
    }

    /// @param onChange Run when the user changes a setting other than the credential.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param model The username and password credentials to choose from.
    public void setCredentialModel(final SelectionListModel<Credential, SimpleSelectionItemWrapper<Credential>> model) {
        credential.setModel(model);
    }

    /// @param onCredentialChange Given the credential the user picks, or `null` for none.
    public void setOnCredentialChange(final Consumer<Credential> onCredentialChange) {
        credential.addValueChangeHandler(e -> onCredentialChange.accept(e.getValue()));
    }

    /// @param credential The credential to show, or `null` for none.
    public void setCredential(final Credential credential) {
        this.credential.setValue(credential);
    }

    /// @param note A note about the credential, e.g. that it can't be shown, or `null` for none.
    public void setCredentialNote(final String note) {
        credentialNote.setText(note);
        credentialNote.setVisible(note != null);
    }

    /// @return The JDBC URL as entered.
    public String getUrl() {
        return url.getValue();
    }

    /// @param url The JDBC URL, or `null`.
    public void setUrl(final String url) {
        this.url.setValue(url);
    }

    /// @return The connection properties as entered, one `name=value` per line.
    public String getConnectionProperties() {
        return connectionProperties.getValue();
    }

    /// @param connectionProperties The connection properties, one `name=value` per line.
    public void setConnectionProperties(final String connectionProperties) {
        this.connectionProperties.setValue(connectionProperties);
    }

    /// @return The largest connection pool.
    public int getMaxPoolSize() {
        return maxPoolSize.getIntValue();
    }

    /// @param maxPoolSize The largest connection pool.
    public void setMaxPoolSize(final int maxPoolSize) {
        this.maxPoolSize.setValue(maxPoolSize);
    }

    /// @return The rows fetched in each round trip.
    public int getFetchSize() {
        return fetchSize.getIntValue();
    }

    /// @param fetchSize The rows fetched in each round trip.
    public void setFetchSize(final int fetchSize) {
        this.fetchSize.setValue(fetchSize);
    }

    /// @return The shortest automatic dashboard refresh, in minutes; 0 for none.
    public int getMinRefreshMinutes() {
        return minRefreshMinutes.getIntValue();
    }

    /// @param minutes The shortest automatic dashboard refresh, in minutes.
    public void setMinRefreshMinutes(final int minutes) {
        minRefreshMinutes.setValue(minutes);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        url.setReadOnly(readOnly);
        credential.setReadOnly(readOnly);
        connectionProperties.setReadOnly(readOnly);
        maxPoolSize.setReadOnly(readOnly);
        fetchSize.setReadOnly(readOnly);
        minRefreshMinutes.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, JdbcSection> {

    }
}

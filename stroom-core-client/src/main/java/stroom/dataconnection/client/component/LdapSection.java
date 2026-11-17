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
import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.LdapSearchScope;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.DescriptionHTML;
import stroom.widget.form.client.TextBox;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.selection.client.SelectionListModel;
import stroom.widget.selection.client.SimpleSelectionItemWrapper;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;

/// The settings of an LDAP Data Connection, in its Settings tab ([DataConnectionSettings]): the
/// directory's URL, StartTLS, the credential to bind with, where and how far searches reach, the page
/// size, the shortest automatic dashboard refresh, and the attributes read from each entry, which can
/// be detected or added by name.
public class LdapSection extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox url;
    @UiField
    TickBox startTls;
    @UiField
    DescriptionHTML credentialNote;
    @UiField
    SelectionBox<Credential> credential;
    @UiField
    TextBox baseDn;
    @UiField
    SelectionBox<LdapSearchScope> scope;
    @UiField
    ValueSpinner pageSize;
    @UiField
    ValueSpinner minRefreshMinutes;
    @UiField
    Button detectAttributes;
    @UiField
    DataColumnList attributes;

    private Runnable onChange = () -> {
    };

    /// Makes the section, empty, searching the whole subtree.
    public LdapSection() {
        initWidget(BINDER.createAndBindUi(this));
        scope.addItems(LdapSearchScope.values());
        scope.setValue(LdapSearchScope.SUBTREE);
        url.addValueChangeHandler(e -> onChange.run());
        startTls.addValueChangeHandler(e -> onChange.run());
        baseDn.addValueChangeHandler(e -> onChange.run());
        scope.addValueChangeHandler(e -> onChange.run());
        pageSize.addValueChangeHandler(e -> onChange.run());
        minRefreshMinutes.addValueChangeHandler(e -> onChange.run());
        attributes.setOnChange(() -> onChange.run());
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

    /// @param onDetectAttributes Run when Detect Attributes is pressed.
    public void setOnDetectAttributes(final Runnable onDetectAttributes) {
        detectAttributes.setOnClick(onDetectAttributes);
    }

    /// @return The Detect Attributes button, which spins while the attributes are detected.
    public TaskMonitorFactory getDetectAttributesTaskMonitor() {
        return detectAttributes;
    }

    /// @param onAddAttribute Run when Add Attribute is pressed, for the owner to ask for its name.
    public void setOnAddAttribute(final Runnable onAddAttribute) {
        attributes.setOnAdd(onAddAttribute);
    }

    /// @param name The attribute to add, already checked.
    public void addAttribute(final String name) {
        attributes.addColumn(name);
    }

    /// @return The LDAP URL as entered.
    public String getUrl() {
        return url.getValue();
    }

    /// @param url The LDAP URL, or `null`.
    public void setUrl(final String url) {
        this.url.setValue(url);
    }

    /// @return True if StartTLS is to be used on an `ldap://` connection.
    public boolean isStartTls() {
        return Boolean.TRUE.equals(startTls.getValue());
    }

    /// @param startTls True if StartTLS is to be used on an `ldap://` connection.
    public void setStartTls(final boolean startTls) {
        this.startTls.setValue(startTls);
    }

    /// @return The base DN as entered.
    public String getBaseDn() {
        return baseDn.getValue();
    }

    /// @param baseDn The base DN, or `null`.
    public void setBaseDn(final String baseDn) {
        this.baseDn.setValue(baseDn);
    }

    /// @return The search scope chosen.
    public LdapSearchScope getScope() {
        return scope.getValue();
    }

    /// @param scope The search scope.
    public void setScope(final LdapSearchScope scope) {
        this.scope.setValue(scope);
    }

    /// @return The entries fetched in each page.
    public int getPageSize() {
        return pageSize.getIntValue();
    }

    /// @param pageSize The entries fetched in each page.
    public void setPageSize(final int pageSize) {
        this.pageSize.setValue(pageSize);
    }

    /// @return The shortest automatic dashboard refresh, in minutes; 0 for none.
    public int getMinRefreshMinutes() {
        return minRefreshMinutes.getIntValue();
    }

    /// @param minutes The shortest automatic dashboard refresh, in minutes.
    public void setMinRefreshMinutes(final int minutes) {
        minRefreshMinutes.setValue(minutes);
    }

    /// @return The attributes read from each entry, with the types chosen.
    public List<DataColumn> getAttributes() {
        return attributes.getColumns();
    }

    /// @param attributes The attributes read from each entry.
    public void setAttributes(final List<DataColumn> attributes) {
        this.attributes.setColumns(attributes);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        url.setReadOnly(readOnly);
        startTls.setReadOnly(readOnly);
        credential.setReadOnly(readOnly);
        baseDn.setReadOnly(readOnly);
        scope.setReadOnly(readOnly);
        pageSize.setReadOnly(readOnly);
        minRefreshMinutes.setReadOnly(readOnly);
        // Detecting needs permission to edit the document
        detectAttributes.setEnabled(!readOnly);
        attributes.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, LdapSection> {

    }
}

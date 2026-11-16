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

/// The settings of an Amazon Athena Data Connection, in its Settings tab ([DataConnectionSettings]):
/// where queries run (region, workgroup, catalog, database, output location), the AWS access key to
/// query with, and the limits on cost (result reuse, the shortest automatic dashboard refresh and the
/// most data a query may scan).
public class AthenaSection extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox region;
    @UiField
    TextBox workGroup;
    @UiField
    TextBox catalog;
    @UiField
    TextBox database;
    @UiField
    TextBox outputLocation;
    @UiField
    DescriptionHTML credentialNote;
    @UiField
    SelectionBox<Credential> credential;
    @UiField
    ValueSpinner resultReuseMinutes;
    @UiField
    ValueSpinner minRefreshMinutes;
    @UiField
    ValueSpinner maxDataScannedMb;

    private Runnable onChange = () -> {
    };

    /// Makes the section, empty.
    public AthenaSection() {
        initWidget(BINDER.createAndBindUi(this));
        for (final TextBox textBox : new TextBox[]{region, workGroup, catalog, database, outputLocation}) {
            textBox.addValueChangeHandler(e -> onChange.run());
        }
        for (final ValueSpinner spinner : new ValueSpinner[]{resultReuseMinutes, minRefreshMinutes, maxDataScannedMb}) {
            spinner.addValueChangeHandler(e -> onChange.run());
        }
        setCredentialNote(null);
    }

    /// @param onChange Run when the user changes a setting other than the credential.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param model The AWS access key credentials to choose from.
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

    /// @return The AWS region as entered.
    public String getRegion() {
        return region.getValue();
    }

    /// @param region The AWS region, or `null`.
    public void setRegion(final String region) {
        this.region.setValue(region);
    }

    /// @return The workgroup as entered.
    public String getWorkGroup() {
        return workGroup.getValue();
    }

    /// @param workGroup The workgroup.
    public void setWorkGroup(final String workGroup) {
        this.workGroup.setValue(workGroup);
    }

    /// @return The data catalog as entered.
    public String getCatalog() {
        return catalog.getValue();
    }

    /// @param catalog The data catalog.
    public void setCatalog(final String catalog) {
        this.catalog.setValue(catalog);
    }

    /// @return The database as entered.
    public String getDatabase() {
        return database.getValue();
    }

    /// @param database The database.
    public void setDatabase(final String database) {
        this.database.setValue(database);
    }

    /// @return The S3 output location as entered.
    public String getOutputLocation() {
        return outputLocation.getValue();
    }

    /// @param outputLocation The S3 output location, or `null` for the workgroup's.
    public void setOutputLocation(final String outputLocation) {
        this.outputLocation.setValue(outputLocation);
    }

    /// @return How old a result may be and still be reused, in minutes.
    public int getResultReuseMinutes() {
        return resultReuseMinutes.getIntValue();
    }

    /// @param minutes How old a result may be and still be reused, in minutes.
    public void setResultReuseMinutes(final int minutes) {
        resultReuseMinutes.setValue(minutes);
    }

    /// @return The shortest automatic dashboard refresh, in minutes; 0 for none.
    public int getMinRefreshMinutes() {
        return minRefreshMinutes.getIntValue();
    }

    /// @param minutes The shortest automatic dashboard refresh, in minutes.
    public void setMinRefreshMinutes(final int minutes) {
        minRefreshMinutes.setValue(minutes);
    }

    /// @return The most data a query may scan, in MB; 0 for no limit.
    public int getMaxDataScannedMb() {
        return maxDataScannedMb.getIntValue();
    }

    /// @param megabytes The most data a query may scan, in MB; 0 for no limit.
    public void setMaxDataScannedMb(final int megabytes) {
        maxDataScannedMb.setValue(megabytes);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        for (final TextBox textBox : new TextBox[]{region, workGroup, catalog, database, outputLocation}) {
            textBox.setReadOnly(readOnly);
        }
        credential.setReadOnly(readOnly);
        resultReuseMinutes.setReadOnly(readOnly);
        minRefreshMinutes.setReadOnly(readOnly);
        maxDataScannedMb.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AthenaSection> {

    }
}

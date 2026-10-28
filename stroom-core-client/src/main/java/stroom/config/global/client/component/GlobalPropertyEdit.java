/*
 * Copyright 2016 Crown Copyright
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

package stroom.config.global.client.component;

import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.PasswordBox;
import stroom.widget.form.client.TextArea;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.util.client.Clipboard;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/// An application property's values: its default, database, YAML and effective values, with
/// its data type, source and attributes. Only the database value can be changed, and only when
/// the property is editable.
public class GlobalPropertyEdit extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Label name;
    @UiField
    ToolbarButton copyNameButton;
    @UiField
    TextArea description;
    @UiField
    TextArea defaultValue;
    @UiField
    TextArea yamlValue;
    @UiField
    TickBox useOverride;
    @UiField
    TextArea databaseValue;
    @UiField
    PasswordBox databaseValuePassword;
    @UiField
    TextArea effectiveValue;
    @UiField
    ToolbarButton effectiveValueWarnings;
    @UiField
    ToolbarButton effectiveValueInfo;
    @UiField
    Label dataType;
    @UiField
    ToolbarButton dataTypeHelp;
    @UiField
    Label source;
    @UiField
    TickBox requireRestart;
    @UiField
    TickBox requireUiRestart;
    @UiField
    TickBox readOnly;

    private boolean password;
    private Runnable onChange = () -> {
    };
    private Runnable onShowClusterValues = () -> {
    };
    private Runnable onDataTypeHelp = () -> {
    };

    /// Makes an empty, read-only form.
    public GlobalPropertyEdit() {
        initWidget(BINDER.createAndBindUi(this));
        copyNameButton.setOnClick(this::onCopyNameButton);
        effectiveValueWarnings.addClickHandler(this::onClusterValuesButton);
        effectiveValueInfo.addClickHandler(this::onClusterValuesButton);
        dataTypeHelp.setOnClick(this::onDataTypeHelpButton);
        setEditable(false);

        useOverride.addValueChangeHandler(event -> onChange.run());
        databaseValue.addValueChangeHandler(event -> onChange.run());
        databaseValuePassword.addValueChangeHandler(event -> onChange.run());
    }

    /// Puts the focus on Set Database value.
    public void focus() {
        useOverride.setFocus(true);
    }

    /// @param name The property's full name.
    public void setName(final String name) {
        this.name.setText(name);
    }

    /// @param description What the property controls.
    public void setDescription(final String description) {
        this.description.setText(description);
    }

    /// @param defaultValue The value built into Stroom.
    public void setDefaultValue(final String defaultValue) {
        this.defaultValue.setText(defaultValue);
    }

    /// @param yamlValue The nodes' YAML value, or a message if they differ.
    public void setYamlValue(final String yamlValue) {
        this.yamlValue.setText(yamlValue);
    }

    /// @param effectiveValue The value the nodes use, or a message if they differ.
    public void setEffectiveValue(final String effectiveValue) {
        this.effectiveValue.setText(effectiveValue);
    }

    /// @param dataType The name of the type of value the property takes.
    public void setDataType(final String dataType) {
        this.dataType.setText(dataType);
    }

    /// @param source Where the effective value comes from, or a message if the nodes differ.
    public void setSource(final String source) {
        this.source.setText(source);
    }

    /// @param password Whether the database value is a password, entered in a password box.
    public void setPassword(final boolean password) {
        this.password = password;
        databaseValue.setVisible(!password);
        databaseValuePassword.setVisible(password);
    }

    /// @param databaseValue The database value, in the text area or the password box.
    public void setDatabaseValue(final String databaseValue) {
        if (password) {
            databaseValuePassword.setText(databaseValue);
        } else {
            this.databaseValue.setText(databaseValue);
        }
    }

    /// @return The database value entered, from the text area or the password box.
    public String getDatabaseValue() {
        return password
                ? databaseValuePassword.getText()
                : databaseValue.getText();
    }

    /// @param useOverride Whether Set Database value is ticked.
    public void setUseOverride(final boolean useOverride) {
        this.useOverride.setValue(useOverride);
    }

    /// @return Whether Set Database value is ticked.
    public boolean isUseOverride() {
        return useOverride.getValue();
    }

    /// @param requireRestart Whether a change only takes effect after every node restarts.
    public void setRequireRestart(final boolean requireRestart) {
        this.requireRestart.setValue(requireRestart);
    }

    /// @param requireUiRestart Whether a change only takes effect after the UI nodes restart.
    public void setRequireUiRestart(final boolean requireUiRestart) {
        this.requireUiRestart.setValue(requireUiRestart);
    }

    /// Ticks Read Only if the property can't be edited, and lets the database value be changed
    /// only when it can be edited and Set Database value is ticked.
    ///
    /// @param editable Whether the property can be changed here.
    public void setEditable(final boolean editable) {
        readOnly.setValue(!editable);
        useOverride.setEnabled(editable);
        final boolean valueEditable = editable && useOverride.getValue();
        databaseValue.setReadOnly(!valueEditable);
        databaseValuePassword.setReadOnly(!valueEditable);
    }

    /// Hides both cluster values buttons, for a single node cluster.
    public void hideClusterValues() {
        effectiveValueWarnings.setVisible(false);
        effectiveValueInfo.setVisible(false);
    }

    /// Shows the cluster values warning button, the nodes differing in some way.
    ///
    /// @param message How the nodes differ, added to the button's title.
    public void showClusterValuesWarning(final String message) {
        effectiveValueWarnings.setTitle("Show Cluster Values (" + message + ")");
        effectiveValueWarnings.setVisible(true);
        effectiveValueInfo.setVisible(false);
    }

    /// Shows the cluster values info button, all the nodes having the same effective value.
    public void showClusterValuesInfo() {
        effectiveValueWarnings.setVisible(false);
        effectiveValueInfo.setVisible(true);
    }

    /// @param onChange Called when Set Database value or the database value changes.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param onShowClusterValues Called when either cluster values button is pressed.
    public void setOnShowClusterValues(final Runnable onShowClusterValues) {
        this.onShowClusterValues = onShowClusterValues;
    }

    /// @param onDataTypeHelp Called when the data type's help button is pressed.
    public void setOnDataTypeHelp(final Runnable onDataTypeHelp) {
        this.onDataTypeHelp = onDataTypeHelp;
    }

    private void onCopyNameButton() {
        Clipboard.writeText(name.getText());
    }

    private void onClusterValuesButton(final ClickEvent event) {
        onShowClusterValues.run();
    }

    private void onDataTypeHelpButton() {
        onDataTypeHelp.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, GlobalPropertyEdit> {

    }
}

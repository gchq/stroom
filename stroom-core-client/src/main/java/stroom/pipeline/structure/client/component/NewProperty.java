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

package stroom.pipeline.structure.client.component;

import stroom.item.client.SelectionBox;
import stroom.pipeline.structure.client.component.PropertyListController.Source;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;

/// The Edit Property dialog's content, for a property of a pipeline element: the element, the
/// property's name and description, where its value comes from, and the value, in the one editor
/// for the property's type.
public class NewProperty extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Label element;
    @UiField
    Label name;
    @UiField
    Label description;
    @UiField
    FormGroup defaultValueGroup;
    @UiField
    Label defaultValue;
    @UiField
    FormGroup inheritedValueGroup;
    @UiField
    Label inheritedValue;
    @UiField
    SelectionBox<Source> source;
    @UiField
    SelectionBox<String> booleanValue;
    @UiField
    ValueSpinner numberValue;
    @UiField
    TextBox textValue;
    @UiField
    SelectionBox<String> streamTypeValue;
    @UiField
    SelectionBox<String> volumeGroupValue;
    @UiField
    DropDownBox documentValue;

    private Consumer<Source> onSourceChange = source -> {
    };
    private Runnable onValueChange = () -> {
    };

    /// Makes the content.
    public NewProperty() {
        initWidget(BINDER.createAndBindUi(this));
        source.addItem(Source.DEFAULT);
        source.addItem(Source.INHERIT);
        source.addItem(Source.LOCAL);
        booleanValue.addItem("true");
        booleanValue.addItem("false");
        showValueEditor(ValueEditor.TEXT);
    }

    /// Puts the focus on the source.
    public void focus() {
        source.focus();
    }

    /// @param element The element's name, as shown.
    public void setElement(final String element) {
        this.element.setText(element);
    }

    /// @param name The property's name.
    public void setName(final String name) {
        this.name.setText(name);
    }

    /// @param description The property's description.
    public void setDescription(final String description) {
        this.description.setText(description);
    }

    /// @param defaultValue The property's default value, shown if there is one.
    public void setDefaultValue(final String defaultValue) {
        if (defaultValue == null || defaultValue.isEmpty()) {
            defaultValueGroup.setVisible(false);
        } else {
            defaultValueGroup.setVisible(true);
            this.defaultValue.setText(defaultValue);
        }
    }

    /// Shows the value inherited from a parent pipeline, if there is one.
    ///
    /// @param inheritedFrom  The pipeline it is inherited from.
    /// @param inheritedValue The value.
    public void setInherited(final String inheritedFrom, final String inheritedValue) {
        if (inheritedValue == null || inheritedValue.isEmpty()) {
            inheritedValueGroup.setVisible(false);
        } else {
            inheritedValueGroup.setVisible(true);
            inheritedValueGroup.setLabel("Inherited Value From '" + inheritedFrom + "'");
            this.inheritedValue.setText(inheritedValue);
        }
    }

    /// @param source A source to offer as well as Default, Inherit and Local, e.g. Embedded.
    public void addSource(final Source source) {
        this.source.addItem(source);
    }

    /// @return Where the value comes from.
    public Source getSource() {
        return source.getValue();
    }

    /// @param source Where the value comes from.
    public void setSource(final Source source) {
        this.source.setValue(source);
    }

    /// Shows one value editor, hiding the others.
    ///
    /// @param editor The editor to show.
    public void showValueEditor(final ValueEditor editor) {
        booleanValue.setVisible(editor == ValueEditor.BOOLEAN);
        numberValue.setVisible(editor == ValueEditor.NUMBER);
        textValue.setVisible(editor == ValueEditor.TEXT);
        streamTypeValue.setVisible(editor == ValueEditor.STREAM_TYPE);
        volumeGroupValue.setVisible(editor == ValueEditor.VOLUME_GROUP);
        documentValue.setVisible(editor == ValueEditor.DOCUMENT);
    }

    /// @return The boolean value chosen, "true" or "false".
    public String getBooleanValue() {
        return booleanValue.getValue();
    }

    /// @param value The boolean value, "true" or "false".
    public void setBooleanValue(final String value) {
        booleanValue.setValue(value);
    }

    /// @return The number entered.
    public int getNumberValue() {
        return numberValue.getIntValue();
    }

    /// @param value The number.
    public void setNumberValue(final Long value) {
        numberValue.setValue(value);
    }

    /// @return The text entered.
    public String getTextValue() {
        return textValue.getText();
    }

    /// Sets the text, and whether it can be changed (an embedded document's name can't).
    ///
    /// @param value   The text.
    /// @param enabled Whether it can be changed.
    public void setTextValue(final String value, final boolean enabled) {
        textValue.setEnabled(enabled);
        textValue.setText(value);
    }

    /// @return The stream type chosen.
    public String getStreamTypeValue() {
        return streamTypeValue.getValue();
    }

    /// @param value The stream type.
    public void setStreamTypeValue(final String value) {
        streamTypeValue.setValue(value);
    }

    /// @param streamTypes The stream types to choose from.
    public void setStreamTypes(final List<String> streamTypes) {
        streamTypeValue.addItems(streamTypes);
    }

    /// @return The volume group chosen.
    public String getVolumeGroupValue() {
        return volumeGroupValue.getValue();
    }

    /// @param value The volume group.
    public void setVolumeGroupValue(final String value) {
        volumeGroupValue.setValue(value);
    }

    /// @param volumeGroups The volume groups to choose from, or none.
    public void setVolumeGroups(final List<String> volumeGroups) {
        volumeGroupValue.clear();
        volumeGroupValue.setNonSelectString("");
        volumeGroupValue.addItems(volumeGroups);
    }

    /// @return The document picker, for its controller to attach to.
    public DropDownBox getDocumentValueBox() {
        return documentValue;
    }

    /// @param onSourceChange Given the source the user chooses.
    public void setOnSourceChange(final Consumer<Source> onSourceChange) {
        this.onSourceChange = onSourceChange;
    }

    /// @param onValueChange Run when the user changes the value in the boolean, number, text,
    ///                      stream type or volume group editor.
    public void setOnValueChange(final Runnable onValueChange) {
        this.onValueChange = onValueChange;
    }

    @UiHandler("source")
    void onSource(final ValueChangeEvent<Source> event) {
        onSourceChange.accept(source.getValue());
    }

    @UiHandler({"booleanValue", "streamTypeValue", "volumeGroupValue", "textValue"})
    void onValue(final ValueChangeEvent<String> event) {
        onValueChange.run();
    }

    @UiHandler("numberValue")
    void onNumber(final ValueChangeEvent<Long> event) {
        onValueChange.run();
    }


    // --------------------------------------------------------------------------------


    /// The editors a property's value can be shown in.
    public enum ValueEditor {
        BOOLEAN,
        NUMBER,
        TEXT,
        STREAM_TYPE,
        VOLUME_GROUP,
        DOCUMENT
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, NewProperty> {

    }
}

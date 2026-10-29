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

package stroom.receive.content.client.component;

import stroom.item.client.SelectionBox;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.client.component.ExpressionEditor;
import stroom.receive.content.shared.TemplateType;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextArea;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/// A content template: its name and description, what it creates (a processor filter, or a
/// pipeline inheriting from one), from which pipeline, the new processor's priority and task limit,
/// and the expression choosing the data it applies to.
public class ContentTemplateEdit extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox name;
    @UiField
    TextArea description;
    @UiField
    SelectionBox<TemplateType> templateTypeSelectionBox;
    @UiField
    FormGroup copyDependenciesFormGroup;
    @UiField
    TickBox copyDependenciesCheckBox;
    @UiField
    DropDownBox pipeline;
    @UiField
    ValueSpinner processorPriority;
    @UiField
    ValueSpinner processorMaxConcurrent;
    @UiField
    ExpressionEditor expressionEditor;

    /// Makes the form, with the processor's defaults.
    public ContentTemplateEdit() {
        initWidget(BINDER.createAndBindUi(this));
        templateTypeSelectionBox.addValueChangeHandler(this::onTemplateTypeChange);

        final List<TemplateType> templateTypes = Arrays.stream(TemplateType.values())
                .sorted()
                .collect(Collectors.toList());
        templateTypeSelectionBox.addItems(templateTypes);

        processorPriority.setMin(ProcessorFilter.MIN_PRIORITY);
        processorPriority.setMax(ProcessorFilter.MAX_PRIORITY);
        processorPriority.setValue(ProcessorFilter.DEFAULT_PRIORITY);

        processorMaxConcurrent.setMin(ProcessorFilter.MIN_MAX_PROCESSING_TASKS);
        processorMaxConcurrent.setMax(ProcessorFilter.MAX_MAX_PROCESSING_TASKS);
        processorMaxConcurrent.setValue(ProcessorFilter.DEFAULT_MAX_PROCESSING_TASKS);
    }

    /// Puts the keyboard focus on the name.
    public void focus() {
        name.setFocus(true);
    }

    /// @return The expression's editor, which its controller attaches the editor's controller to.
    public ExpressionEditor getExpressionEditor() {
        return expressionEditor;
    }

    /// @return The pipeline picker, for its controller to attach to.
    public DropDownBox getPipelineBox() {
        return pipeline;
    }

    /// @return The name typed.
    public String getName() {
        return name.getText();
    }

    /// @param name The template's name.
    public void setName(final String name) {
        this.name.setText(name);
    }

    /// @return The description typed.
    public String getDescription() {
        return description.getText();
    }

    /// @param description The template's description.
    public void setDescription(final String description) {
        this.description.setText(description);
    }

    /// @return What the template creates.
    public TemplateType getTemplateType() {
        return templateTypeSelectionBox.getValue();
    }

    /// @param templateType What the template creates; only a pipeline that inherits can copy its
    ///                     dependencies.
    public void setTemplateType(final TemplateType templateType) {
        templateTypeSelectionBox.setValue(templateType);
        updateCopyDependenciesCheckBox(templateType);
    }

    /// @return Whether Copy Pipeline Element Dependencies is ticked.
    public boolean isCopyDependencies() {
        return copyDependenciesCheckBox.getValue();
    }

    /// @param copyDependencies Whether to tick Copy Pipeline Element Dependencies.
    public void setCopyDependencies(final boolean copyDependencies) {
        copyDependenciesCheckBox.setValue(copyDependencies);
    }

    /// @return The new processor's priority.
    public int getProcessorPriority() {
        return processorPriority.getIntValue();
    }

    /// @param value The new processor's priority.
    public void setProcessorPriority(final int value) {
        processorPriority.setValue(value);
    }

    /// @return The new processor's most tasks at once, 0 for no limit.
    public int getProcessorMaxConcurrent() {
        return processorMaxConcurrent.getIntValue();
    }

    /// @param value The new processor's most tasks at once, 0 for no limit.
    public void setProcessorMaxConcurrent(final int value) {
        processorMaxConcurrent.setValue(value);
    }

    private void onTemplateTypeChange(final ValueChangeEvent<TemplateType> event) {
        setTemplateType(event.getValue());
    }

    private void updateCopyDependenciesCheckBox(final TemplateType templateType) {
        final boolean isEnabled;
        if (templateType == TemplateType.INHERIT_PIPELINE) {
            isEnabled = true;
        } else {
            isEnabled = false;
            copyDependenciesCheckBox.setValue(false);
        }
        copyDependenciesCheckBox.setEnabled(isEnabled);
        copyDependenciesFormGroup.setDisabled(!isEnabled);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ContentTemplateEdit> {

    }
}

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

package stroom.receive.content.client.view;

import stroom.item.client.SelectionBox;
import stroom.processor.shared.ProcessorFilter;
import stroom.receive.content.client.presenter.ContentTemplateEditPresenter.ContentTemplateEditView;
import stroom.receive.content.shared.TemplateType;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ContentTemplateEditViewImpl extends ViewImpl implements ContentTemplateEditView {

    private final Widget widget;

    @UiField
    TextBox name;
    @UiField
    TextArea description;
    @UiField
    SelectionBox<TemplateType> templateTypeSelectionBox;
    @UiField
    FormGroup copyDependenciesFormGroup;
    @UiField
    CustomCheckBox copyDependenciesCheckBox;
    @UiField
    SimplePanel pipeline;
    @UiField
    ValueSpinner processorPriority;
    @UiField
    ValueSpinner processorMaxConcurrent;

    @UiField
    SimplePanel expression;

    @Inject
    public ContentTemplateEditViewImpl(final ContentTemplateEditViewImpl.Binder binder) {
        widget = binder.createAndBindUi(this);

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

        // TODO @AT There must be a better way of setting the focus than having to use a timer
        new Timer() {
            @Override
            public void run() {
                name.setFocus(true);
                name.setTabIndex(0);
            }
        }.schedule(100);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        name.setFocus(true);
    }

    @Override
    public void setExpressionView(final View view) {
        expression.setWidget(view.asWidget());
    }

    @Override
    public String getName() {
        return name.getText();
    }

    @Override
    public void setName(final String name) {
        this.name.setText(name);
    }

    @Override
    public String getDescription() {
        return description.getText();
    }

    @Override
    public void setDescription(final String description) {
        this.description.setText(description);
    }

    @Override
    public SelectionBox<TemplateType> getTemplateTypeSelectionBox() {
        return templateTypeSelectionBox;
    }

    @Override
    public TemplateType getTemplateType() {
        return templateTypeSelectionBox.getValue();
    }

    @Override
    public boolean isCopyDependencies() {
        return copyDependenciesCheckBox.getValue();
    }

    @Override
    public void setCopyDependencies(final boolean copyDependencies) {
        copyDependenciesCheckBox.setValue(copyDependencies);
    }

    @Override
    public void setTemplateType(final TemplateType templateType) {
        templateTypeSelectionBox.setValue(templateType);
        updateCopyDependenciesCheckBox(templateType);
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

    @Override
    public void setPipelineSelector(final View view) {
        final Widget w = view.asWidget();
        w.getElement().getStyle().setWidth(100, Unit.PCT);
        w.getElement().getStyle().setMargin(0, Unit.PX);
        pipeline.setWidget(w);
    }

    @Override
    public int getProcessorPriority() {
        return processorPriority.getIntValue();
    }

    @Override
    public void setProcessorPriority(final int value) {
        processorPriority.setValue(value);
    }

    @Override
    public int getProcessorMaxConcurrent() {
        return processorMaxConcurrent.getIntValue();
    }

    @Override
    public void setProcessorMaxConcurrent(final int value) {
        processorMaxConcurrent.setValue(value);
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, ContentTemplateEditViewImpl> {

    }
}

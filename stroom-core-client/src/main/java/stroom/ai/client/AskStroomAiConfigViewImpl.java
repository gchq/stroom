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

package stroom.ai.client;

import stroom.ai.client.AskStroomAiConfigPresenter.AskStroomAiConfigView;
import stroom.ai.client.AskStroomAiPresenter.DockBehaviour;
import stroom.ai.client.AskStroomAiPresenter.DockLocation;
import stroom.ai.client.AskStroomAiPresenter.DockType;
import stroom.item.client.SelectionBox;
import stroom.widget.button.client.Button;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class AskStroomAiConfigViewImpl
        extends ViewWithUiHandlers<AskStroomAiConfigUiHandlers>
        implements AskStroomAiConfigView {

    private final Widget widget;

    @UiField
    SelectionBox<DockType> dockTypeSelectionBox;
    @UiField
    SelectionBox<DockLocation> dockLocationSelectionBox;
    @UiField
    ValueSpinner maximumBatchSize;
    @UiField
    ValueSpinner maximumTableInputRows;
    @UiField
    TextArea tableQuerySystemPrompt;
    @UiField
    TextArea tableQueryUserPrompt;
    @UiField
    TextArea summaryMergePrompt;
    @UiField
    SimplePanel modelRef;
    @UiField
    Button setDefault;

    @Inject
    public AskStroomAiConfigViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        setDefault.setVisible(false);

        // Only DIALOG and DOCK modes are supported.
        dockTypeSelectionBox.addItem(DockType.DIALOG);
        dockTypeSelectionBox.addItem(DockType.DOCK);
        dockTypeSelectionBox.setValue(DockType.DIALOG);

        dockLocationSelectionBox.addItem(DockLocation.RIGHT);
        dockLocationSelectionBox.addItem(DockLocation.LEFT);
        dockLocationSelectionBox.addItem(DockLocation.TOP);
        dockLocationSelectionBox.addItem(DockLocation.BOTTOM);
        dockLocationSelectionBox.setValue(DockLocation.RIGHT);

        maximumBatchSize.setMin(1);
        maximumBatchSize.setMax(1000000);
        maximumTableInputRows.setMin(1);
        maximumTableInputRows.setMax(1000000);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        maximumBatchSize.focus();
    }

    @Override
    public void allowSetDefault(final boolean allow) {
        setDefault.setVisible(allow);
    }

    @Override
    public void setMaximumBatchSize(final int maximumBatchSize) {
        this.maximumBatchSize.setValue(maximumBatchSize);
    }

    @Override
    public int getMaximumBatchSize() {
        return maximumBatchSize.getIntValue();
    }

    @Override
    public void setMaximumTableInputRows(final int maximumTableInputRows) {
        this.maximumTableInputRows.setValue(maximumTableInputRows);
    }

    @Override
    public int getMaximumTableInputRows() {
        return maximumTableInputRows.getIntValue();
    }

    @Override
    public void setTableQuerySystemPrompt(final String prompt) {
        this.tableQuerySystemPrompt.setText(prompt);
    }

    @Override
    public String getTableQuerySystemPrompt() {
        return tableQuerySystemPrompt.getText();
    }

    @Override
    public void setTableQueryUserPrompt(final String prompt) {
        this.tableQueryUserPrompt.setText(prompt);
    }

    @Override
    public String getTableQueryUserPrompt() {
        return tableQueryUserPrompt.getText();
    }

    @Override
    public void setSummaryMergePrompt(final String prompt) {
        this.summaryMergePrompt.setText(prompt);
    }

    @Override
    public String getSummaryMergePrompt() {
        return summaryMergePrompt.getText();
    }

    @Override
    public void setModelRefSelection(final View view) {
        this.modelRef.setWidget(view.asWidget());
    }

    @UiHandler("setDefault")
    public void onSetDefaultClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onSetDefault(setDefault);
        }
    }

    @UiHandler("dockTypeSelectionBox")
    public void onDockTypeSelectionBox(final ValueChangeEvent<DockType> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDockBehaviourChange(getDockBehaviour());
        }
    }

    @UiHandler("dockLocationSelectionBox")
    public void onDockLocationSelectionBox(final ValueChangeEvent<DockLocation> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDockBehaviourChange(getDockBehaviour());
        }
    }

    @Override
    public void setDockBehaviour(final DockBehaviour dockBehaviour) {
        dockTypeSelectionBox.setValue(dockBehaviour.getDockType());
        dockLocationSelectionBox.setValue(dockBehaviour.getDockLocation());
    }

    @Override
    public DockBehaviour getDockBehaviour() {
        return new DockBehaviour(dockTypeSelectionBox.getValue(), dockLocationSelectionBox.getValue());
    }

    public interface Binder extends UiBinder<Widget, AskStroomAiConfigViewImpl> {

    }
}

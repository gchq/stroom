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

import stroom.ai.client.AiConfigGeneralUiHandlers;
import stroom.ai.client.AiConfigGeneralPresenter.AiConfigGeneralView;
import stroom.ai.client.AskStroomAiPresenter.DockBehaviour;
import stroom.ai.client.AskStroomAiPresenter.DockLocation;
import stroom.ai.client.AskStroomAiPresenter.DockType;
import stroom.item.client.SelectionBox;
import stroom.widget.valuespinner.client.ValueSpinner;

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

public class AiConfigGeneralViewImpl
        extends ViewWithUiHandlers<AiConfigGeneralUiHandlers>
        implements AiConfigGeneralView {

    private final Widget widget;

    @UiField
    SimplePanel modelRef;
    @UiField
    SelectionBox<DockType> dockTypeSelectionBox;
    @UiField
    SelectionBox<DockLocation> dockLocationSelectionBox;
    @UiField
    TextArea chatSystemPrompt;
    @UiField
    ValueSpinner maxConversationHistoryMessages;

    @Inject
    public AiConfigGeneralViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        // Only DIALOG and DOCK modes are supported.
        dockTypeSelectionBox.addItem(DockType.DIALOG);
        dockTypeSelectionBox.addItem(DockType.DOCK);
        dockTypeSelectionBox.setValue(DockType.DIALOG);

        dockLocationSelectionBox.addItem(DockLocation.RIGHT);
        dockLocationSelectionBox.addItem(DockLocation.LEFT);
        dockLocationSelectionBox.addItem(DockLocation.TOP);
        dockLocationSelectionBox.addItem(DockLocation.BOTTOM);
        dockLocationSelectionBox.setValue(DockLocation.RIGHT);

        maxConversationHistoryMessages.setMin(1);
        maxConversationHistoryMessages.setMax(200);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        chatSystemPrompt.setFocus(true);
    }

    // ---------------------------------------------------------------------

    @Override
    public void setModelRefSelection(final View view) {
        this.modelRef.setWidget(view.asWidget());
    }

    // ---------------------------------------------------------------------

    @Override
    public void setDockBehaviour(final DockBehaviour dockBehaviour) {
        dockTypeSelectionBox.setValue(dockBehaviour.getDockType());
        dockLocationSelectionBox.setValue(dockBehaviour.getDockLocation());
    }

    @Override
    public DockBehaviour getDockBehaviour() {
        return new DockBehaviour(dockTypeSelectionBox.getValue(), dockLocationSelectionBox.getValue());
    }

    // ---------------------------------------------------------------------

    @Override
    public void setChatSystemPrompt(final String prompt) {
        chatSystemPrompt.setText(prompt);
    }

    @Override
    public String getChatSystemPrompt() {
        return chatSystemPrompt.getText();
    }

    @Override
    public void setMaxConversationHistoryMessages(final int max) {
        maxConversationHistoryMessages.setValue(max);
    }

    @Override
    public int getMaxConversationHistoryMessages() {
        return maxConversationHistoryMessages.getIntValue();
    }

    // ---------------------------------------------------------------------

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

    // ---------------------------------------------------------------------

    public interface Binder extends UiBinder<Widget, AiConfigGeneralViewImpl> {

    }
}

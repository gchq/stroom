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

package stroom.ai.client.component;

import stroom.ai.client.component.AskStroomAiController.DockBehaviour;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiConfig.DockLocation;
import stroom.ai.shared.AskStroomAiConfig.DockType;
import stroom.item.client.SelectionBox;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.TextArea;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// The General tab of the Ask Stroom AI settings: the model, where the panel is shown, the chat's
/// prompts, how much history is kept and whether each question's detail is shown.
public class AiConfigGeneral extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DropDownBox model;
    @UiField
    SelectionBox<DockType> dockType;
    @UiField
    SelectionBox<DockLocation> dockLocation;
    @UiField
    TextArea chatSystemPrompt;
    @UiField
    TextArea historySummaryPrompt;
    @UiField
    ValueSpinner maxHistorySafetyCapMessages;
    @UiField
    TickBox enableDebugDetail;

    private Consumer<DockBehaviour> onDockBehaviourChange;

    /// Makes an empty tab.
    public AiConfigGeneral() {
        initWidget(BINDER.createAndBindUi(this));

        // Only DIALOG and DOCK modes are supported.
        dockType.addItem(DockType.DIALOG);
        dockType.addItem(DockType.DOCK);
        dockType.setValue(DockType.DIALOG);

        dockLocation.addItem(DockLocation.RIGHT);
        dockLocation.addItem(DockLocation.LEFT);
        dockLocation.addItem(DockLocation.TOP);
        dockLocation.addItem(DockLocation.BOTTOM);
        dockLocation.setValue(DockLocation.RIGHT);

        dockType.addValueChangeHandler(e -> dockBehaviourChanged());
        dockLocation.addValueChangeHandler(e -> dockBehaviourChanged());
    }

    /// @return The model's picker, for its controller to attach to.
    public DropDownBox getModelBox() {
        return model;
    }

    /// Shows the settings, but for the model, using the defaults for any the settings don't have.
    ///
    /// @param config        The settings, or null for the defaults.
    /// @param dockBehaviour Where the panel is shown.
    public void read(final AskStroomAiConfig config, final DockBehaviour dockBehaviour) {
        chatSystemPrompt.setText(NullSafe.getOrElse(
                config,
                AskStroomAiConfig::getChatSystemPrompt,
                AskStroomAiConfig.DEFAULT_CHAT_SYSTEM_PROMPT));
        historySummaryPrompt.setText(NullSafe.getOrElse(
                config,
                AskStroomAiConfig::getHistorySummaryPrompt,
                AskStroomAiConfig.DEFAULT_HISTORY_SUMMARY_PROMPT));
        maxHistorySafetyCapMessages.setValue(NullSafe.getOrElse(
                config,
                AskStroomAiConfig::getMaxHistorySafetyCapMessages,
                AskStroomAiConfig.DEFAULT_MAX_HISTORY_SAFETY_CAP_MESSAGES));
        enableDebugDetail.setValue(NullSafe.getOrElse(
                config,
                AskStroomAiConfig::isEnableDebugDetail,
                AskStroomAiConfig.DEFAULT_ENABLE_DEBUG_DETAIL));
        dockType.setValue(dockBehaviour.getDockType());
        dockLocation.setValue(dockBehaviour.getDockLocation());
    }

    /// Writes the settings shown, but for the model, into the settings' builder.
    ///
    /// @param builder The builder.
    public void write(final AskStroomAiConfig.Builder builder) {
        builder
                .dockType(dockType.getValue())
                .dockLocation(dockLocation.getValue())
                .chatSystemPrompt(chatSystemPrompt.getText())
                .historySummaryPrompt(historySummaryPrompt.getText())
                .maxHistorySafetyCapMessages(maxHistorySafetyCapMessages.getIntValue())
                .enableDebugDetail(enableDebugDetail.getValue());
    }

    /// @return Where the panel is shown, as chosen.
    public DockBehaviour getDockBehaviour() {
        return new DockBehaviour(dockType.getValue(), dockLocation.getValue());
    }

    /// @param onDockBehaviourChange What to run, with where the panel is now to be shown, when the
    ///                              user changes the dock type or location.
    public void setOnDockBehaviourChange(final Consumer<DockBehaviour> onDockBehaviourChange) {
        this.onDockBehaviourChange = onDockBehaviourChange;
    }

    /// Puts the focus on the chat's system prompt.
    public void focus() {
        chatSystemPrompt.setFocus(true);
    }

    private void dockBehaviourChanged() {
        if (onDockBehaviourChange != null) {
            onDockBehaviourChange.accept(getDockBehaviour());
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AiConfigGeneral> {

    }
}

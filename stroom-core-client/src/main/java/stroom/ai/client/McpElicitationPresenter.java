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

package stroom.ai.client;

import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AnswerAiElicitationRequest;
import stroom.alert.client.event.AlertEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/// The dialog for a form an MCP tool asked the user to fill in (MCP elicitation), built from the form's fields:
/// a text box for text and numbers, a list for a choice of one, and tick boxes for true/false and for a choice of
/// several. OK sends the answer, Decline refuses, and Cancel closes the dialog to answer later.
public class McpElicitationPresenter extends MyPresenterWidget<McpElicitationPresenter.McpElicitationView>
        implements McpElicitationUiHandlers {

    private final AskStroomAiClient client;
    private AiChat chat;
    private AiChatMessage message;
    private Consumer<String> onAnswered;
    private final Map<AiElicitationForm.Field, Object> inputs = new LinkedHashMap<>();

    @Inject
    public McpElicitationPresenter(final EventBus eventBus,
                                   final McpElicitationView view,
                                   final AskStroomAiClient client) {
        super(eventBus, view);
        this.client = client;
        view.setUiHandlers(this);
    }

    /// Shows the form.
    ///
    /// @param chat       The chat.
    /// @param message    The ELICITATION message.
    /// @param onAnswered Told the form's new status (e.g. "ACCEPTED") once the answer has been sent.
    public void show(final AiChat chat, final AiChatMessage message, final Consumer<String> onAnswered) {
        this.chat = chat;
        this.message = message;
        this.onAnswered = onAnswered;
        final String server = AiToolMessages.getString(message.getMessage(), "server");
        getView().setForm(server, AiToolMessages.getString(message.getMessage(), "message"),
                buildForm(AiElicitationForm.fields(message.getMessage())));
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(520, 480))
                .caption("Form from " + server)
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        // If the answer is refused (e.g. it doesn't fit the form), the dialog stays open to fix it.
                        answer("ACCEPT", values(), "ACCEPTED", e::hide, e::reset);
                    } else {
                        // Closed without answering: the form still waits.
                        e.hide();
                    }
                })
                .fire();
    }

    @Override
    public void onDecline() {
        answer("DECLINE", null, "DECLINED", () -> HidePopupEvent.builder(this).fire(), () -> {
        });
    }

    private void answer(final String action,
                        final Map<String, String> values,
                        final String status,
                        final Runnable onDone,
                        final Runnable onRefused) {
        client.answerElicitation(new AnswerAiElicitationRequest(chat, message.getId(), action, values), done -> {
            onDone.run();
            onAnswered.accept(status);
        }, error -> {
            onRefused.run();
            AlertEvent.fireError(this, error.getMessage(), null);
        }, this);
    }

    private Widget buildForm(final List<AiElicitationForm.Field> fields) {
        inputs.clear();
        final FlowPanel panel = new FlowPanel();
        for (final AiElicitationForm.Field field : fields) {
            final FlowPanel row = new FlowPanel();
            row.setStyleName("form-group aiElicitation-field");
            // A tick box has its title as its own label.
            if (!"boolean".equals(field.getType()) || field.isMulti()) {
                final Label label = new Label(field.getTitle() + (field.isRequired()
                        ? " *"
                        : ""));
                label.setStyleName("form-label");
                row.add(label);
            }
            if (field.getDescription() != null) {
                final Label description = new Label(field.getDescription());
                description.setStyleName("aiMemory-intro");
                row.add(description);
            }
            row.add(input(field));
            panel.add(row);
        }
        return panel;
    }

    private Widget input(final AiElicitationForm.Field field) {
        if (field.isMulti()) {
            final FlowPanel choices = new FlowPanel();
            final List<CustomCheckBox> boxes = new ArrayList<>();
            for (int i = 0; i < field.getValues().size(); i++) {
                final CustomCheckBox box = new CustomCheckBox();
                box.setLabel(field.getLabels().get(i));
                boxes.add(box);
                choices.add(box);
            }
            inputs.put(field, boxes);
            return choices;
        } else if ("boolean".equals(field.getType())) {
            final CustomCheckBox box = new CustomCheckBox();
            box.setLabel(field.getTitle() + (field.isRequired()
                    ? " *"
                    : ""));
            inputs.put(field, box);
            return box;
        } else if (!field.getValues().isEmpty()) {
            final ListBox list = new ListBox();
            if (!field.isRequired()) {
                list.addItem("", "");
            }
            for (int i = 0; i < field.getValues().size(); i++) {
                list.addItem(field.getLabels().get(i), field.getValues().get(i));
            }
            inputs.put(field, list);
            return list;
        }
        final TextBox text = new TextBox();
        text.addStyleName("w-100");
        if ("date".equals(field.getFormat())) {
            text.getElement().setAttribute("placeholder", "YYYY-MM-DD");
        } else if ("date-time".equals(field.getFormat())) {
            text.getElement().setAttribute("placeholder", "YYYY-MM-DDThh:mm:ssZ");
        } else if ("email".equals(field.getFormat())) {
            text.getElement().setAttribute("placeholder", "name@example.com");
        }
        inputs.put(field, text);
        return text;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> values() {
        final Map<String, String> values = new HashMap<>();
        inputs.forEach((field, input) -> {
            if (input instanceof final TextBox text) {
                values.put(field.getName(), text.getText());
            } else if (input instanceof final ListBox list) {
                values.put(field.getName(), list.getSelectedIndex() >= 0
                        ? list.getSelectedValue()
                        : "");
            } else if (input instanceof final CustomCheckBox box) {
                values.put(field.getName(), String.valueOf(Boolean.TRUE.equals(box.getValue())));
            } else if (input instanceof List<?>) {
                final List<CustomCheckBox> boxes = (List<CustomCheckBox>) input;
                final JSONArray chosen = new JSONArray();
                for (int i = 0; i < boxes.size(); i++) {
                    if (Boolean.TRUE.equals(boxes.get(i).getValue())) {
                        chosen.set(chosen.size(), new JSONString(field.getValues().get(i)));
                    }
                }
                values.put(field.getName(), chosen.toString());
            }
        });
        return values;
    }


    // --------------------------------------------------------------------------------


    public interface McpElicitationView extends View, HasUiHandlers<McpElicitationUiHandlers> {

        /// Shows the form.
        ///
        /// @param server The server that asked.
        /// @param text   What the server said, shown as plain text.
        /// @param fields The form's fields.
        void setForm(String server, String text, Widget fields);
    }
}

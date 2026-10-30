/*
 * Copyright 2023 Crown Copyright
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

package stroom.security.client.component;

import stroom.item.client.SelectionBox;
import stroom.security.shared.HashAlgorithm;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextArea;
import stroom.widget.form.client.TextBox;
import stroom.widget.text.client.Label;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

/// An API key: its owner, name, hash algorithm, prefix, the key itself just after it is made,
/// whether it is enabled, comments and expiry. Which fields show and can be changed depends on
/// the [ApiKeyEditorMode].
public class ApiKeyEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DropDownBox owner;
    @UiField
    TextBox nameTextBox;
    @UiField
    TextArea commentsTextArea;
    @UiField
    DateTimeBox expiresOnDateBox;
    @UiField
    SelectionBox<HashAlgorithm> hashAlgorithmSelectionBox;
    @UiField
    TickBox enabledCheckBox;
    @UiField
    FormGroup prefixFormGroup;
    @UiField
    TextBox prefixTextBox;
    @UiField
    FormGroup apiKeyFormGroup;
    @UiField
    TextArea apiKeyTextArea;
    @UiField
    Toolbar apiKeyButtonPanel;
    @UiField
    ToolbarButton copyButton;
    @UiField
    Label copyToClipboardLabel;

    private ApiKeyEditorMode mode;
    private Runnable onCopy = () -> {
    };

    /// Makes an empty form, offering every hash algorithm. The name takes the focus once shown.
    public ApiKeyEditor() {
        initWidget(BINDER.createAndBindUi(this));
        copyButton.setOnClick(this::onCopyButton);
        addAttachHandler(event -> focus());
        hashAlgorithmSelectionBox.addItems(Arrays.stream(HashAlgorithm.values())
                .sorted(Comparator.comparing(HashAlgorithm::getDisplayValue))
                .collect(Collectors.toList()));
    }

    /// @return The owner picker, for its controller to attach to.
    public DropDownBox getOwnerBox() {
        return owner;
    }

    /// @return The expiry date's box, for its controller to attach to.
    public DateTimeBox getExpiresOnBox() {
        return expiresOnDateBox;
    }

    /// Shows the fields the mode needs, and lets the user change only what the mode allows.
    ///
    /// @param mode Whether the key is being made, has just been made, or is being edited.
    public void setMode(final ApiKeyEditorMode mode) {
        this.mode = mode;
        final boolean isPrefixVisible = !ApiKeyEditorMode.PRE_CREATE.equals(mode);
        final boolean isApiKeyVisible = ApiKeyEditorMode.POST_CREATE.equals(mode);
        prefixTextBox.setVisible(isPrefixVisible);
        prefixFormGroup.setVisible(isPrefixVisible);
        apiKeyTextArea.setVisible(isApiKeyVisible);
        apiKeyFormGroup.setVisible(isApiKeyVisible);
        apiKeyButtonPanel.setVisible(isApiKeyVisible);

        // POST_CREATE is just to view what has been created, so the user can't change anything
        final boolean creating = ApiKeyEditorMode.PRE_CREATE.equals(mode);
        final boolean editable = creating || ApiKeyEditorMode.EDIT.equals(mode);
        expiresOnDateBox.setEnabled(creating);
        nameTextBox.setEnabled(editable);
        commentsTextArea.setEnabled(editable);
        enabledCheckBox.setEnabled(editable);
        hashAlgorithmSelectionBox.setEnabled(creating);
    }

    /// @return Whether the key is being made, has just been made, or is being edited.
    public ApiKeyEditorMode getMode() {
        return mode;
    }

    /// @param name The key's name.
    public void setName(final String name) {
        nameTextBox.setText(name);
    }

    /// @return The key's name.
    public String getName() {
        return nameTextBox.getText();
    }

    /// @param prefix The key's prefix.
    public void setPrefix(final String prefix) {
        prefixTextBox.setText(prefix);
    }

    /// @param apiKey The key itself, shown once just after it is made.
    public void setApiKey(final String apiKey) {
        apiKeyTextArea.setValue(apiKey);
    }

    /// @return The key itself.
    public String getApiKey() {
        return apiKeyTextArea.getText();
    }

    /// @param comments The key's comments.
    public void setComments(final String comments) {
        commentsTextArea.setText(comments);
    }

    /// @return The key's comments.
    public String getComments() {
        return commentsTextArea.getText();
    }

    /// @param expiresOn When the key expires, in milliseconds since the epoch.
    public void setExpiresOn(final Long expiresOn) {
        expiresOnDateBox.setValue(expiresOn);
    }

    /// @return When the key expires, or `null` if blank or not a date and time.
    public Long getExpiresOnMs() {
        return expiresOnDateBox.getValue();
    }

    /// @param isEnabled Whether the key can be used.
    public void setEnabled(final boolean isEnabled) {
        enabledCheckBox.setValue(isEnabled);
    }

    /// @return Whether the key can be used.
    public boolean isEnabled() {
        return enabledCheckBox.getValue();
    }

    /// @param hashAlgorithm How the key is hashed.
    public void setHashAlgorithm(final HashAlgorithm hashAlgorithm) {
        hashAlgorithmSelectionBox.setValue(hashAlgorithm);
    }

    /// @return How the key is hashed.
    public HashAlgorithm getHashAlgorithm() {
        return hashAlgorithmSelectionBox.getValue();
    }

    /// Puts the focus on the name, once the browser has shown it.
    public void focus() {
        Scheduler.get().scheduleDeferred(() -> nameTextBox.setFocus(true));
    }

    /// Empties the fields, the key enabled.
    ///
    /// @param expiresOn The expiry to start with, in milliseconds since the epoch.
    public void reset(final Long expiresOn) {
        nameTextBox.setText("");
        commentsTextArea.setText("");
        expiresOnDateBox.setValue(expiresOn);
        enabledCheckBox.setValue(true);
        prefixTextBox.setText("");
        apiKeyTextArea.setText("");
    }

    /// @param copied Whether to say the key has been copied to the clipboard.
    public void setCopiedShown(final boolean copied) {
        copyToClipboardLabel.setVisible(copied);
    }

    /// @param onCopy Called when the copy button is pressed.
    public void setOnCopy(final Runnable onCopy) {
        this.onCopy = onCopy;
    }

    private void onCopyButton() {
        onCopy.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ApiKeyEditor> {

    }
}

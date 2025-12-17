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

package stroom.security.client.view;

import stroom.item.client.SelectionBox;
import stroom.preferences.client.UserPreferencesManager;
import stroom.security.client.presenter.EditApiKeyPresenter.EditApiKeyView;
import stroom.security.client.presenter.EditApiKeyPresenter.Mode;
import stroom.security.client.presenter.UserRefSelectionBoxPresenter;
import stroom.security.shared.HashAlgorithm;
import stroom.svg.client.SvgPresets;
import stroom.util.client.ClipboardUtil;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.customdatebox.client.MyDateBox;
import stroom.widget.form.client.FormGroup;
import stroom.widget.popup.client.view.HideRequestUiHandlers;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

public class EditApiKeyViewImpl
        extends ViewWithUiHandlers<HideRequestUiHandlers>
        implements EditApiKeyView {

    private final Widget widget;
    private Mode mode;

    @UiField
    SimplePanel ownerPanel;
    @UiField
    TextBox nameTextBox;
    @UiField
    TextArea commentsTextArea;
    @UiField
    MyDateBox expiresOnDateBox;
    @UiField
    SelectionBox<HashAlgorithm> hashAlgorithmSelectionBox;
    @UiField
    CustomCheckBox enabledCheckBox;

    @UiField
    FormGroup prefixFormGroup;
    @UiField
    TextBox prefixTextBox;

    @UiField
    FormGroup apiKeyFormGroup;
    @UiField
    TextArea apiKeyTextArea;

    @UiField
    ButtonPanel apiKeyButtonPanel;
    @UiField
    Label copyToClipboardLabel;

    private UserRefSelectionBoxPresenter ownerSelectionView = null;

    @Inject
    public EditApiKeyViewImpl(final Binder binder,
                              final UserPreferencesManager userPreferencesManager) {
        widget = binder.createAndBindUi(this);
        widget.addAttachHandler(event -> focus());
//        this.uiConfigCache = uiConfigCache;

        hashAlgorithmSelectionBox.addItems(Arrays.stream(HashAlgorithm.values())
                .sorted(Comparator.comparing(HashAlgorithm::getDisplayValue))
                .collect(Collectors.toList()));

//        ownerSelectionBox.setDisplayValueFunction(UserName::getUserIdentityForAudit);

        apiKeyTextArea.setEnabled(false);
        prefixTextBox.setEnabled(false);
        expiresOnDateBox.setUtc(userPreferencesManager.isUtc());
        final ButtonView copyApiKeyToClipboardBtn = apiKeyButtonPanel.addButton(SvgPresets.COPY.title(
                "Copy API Key to clipboard"));
        copyApiKeyToClipboardBtn.setEnabled(true);
        // When the copy btn is clicked show a label to give feedback to user, but then remove it after a time
        copyApiKeyToClipboardBtn.addClickHandler(event -> {
            ClipboardUtil.copy(apiKeyTextArea.getText());
            copyToClipboardLabel.setText("API Key copied to clipboard");
            final Timer timer = new Timer() {
                @Override
                public void run() {
                    copyToClipboardLabel.setText("");
                }
            };
            timer.schedule(3_000);
        });
    }

    @Override
    public void setMode(final Mode mode) {
        this.mode = mode;
        final boolean isPrefixVisible;
        final boolean isApiKeyVisible;
        if (Mode.PRE_CREATE.equals(mode)) {
            isPrefixVisible = false;
            isApiKeyVisible = false;
        } else if (Mode.POST_CREATE.equals(mode)) {
            isPrefixVisible = true;
            isApiKeyVisible = true;
        } else {
            isPrefixVisible = true;
            isApiKeyVisible = false;
        }
        prefixTextBox.setVisible(isPrefixVisible);
        prefixFormGroup.setVisible(isPrefixVisible);
        apiKeyTextArea.setVisible(isApiKeyVisible);
        apiKeyFormGroup.setVisible(isApiKeyVisible);
        apiKeyButtonPanel.setVisible(isApiKeyVisible);
        setEnabledStates(mode);
    }

    private void setEnabledStates(final Mode mode) {
        if (Mode.PRE_CREATE.equals(mode)) {
            expiresOnDateBox.setEnabled(true);
            nameTextBox.setEnabled(true);
            commentsTextArea.setEnabled(true);
            enabledCheckBox.setEnabled(true);
            hashAlgorithmSelectionBox.setEnabled(true);
        } else if (Mode.POST_CREATE.equals(mode)) {
            // POST_CREATE is just to view what has been created, so user can't change anything
            expiresOnDateBox.setEnabled(false);
            nameTextBox.setEnabled(false);
            commentsTextArea.setEnabled(false);
            enabledCheckBox.setEnabled(false);
            hashAlgorithmSelectionBox.setEnabled(false);
        } else if (Mode.EDIT.equals(mode)) {
            expiresOnDateBox.setEnabled(false);
            nameTextBox.setEnabled(true);
            commentsTextArea.setEnabled(true);
            enabledCheckBox.setEnabled(true);
            hashAlgorithmSelectionBox.setEnabled(false);
        }
    }

    @Override
    public Mode getMode() {
        return this.mode;
    }

    @Override
    public void setOwnerView(final View ownerSelectionView) {
        ownerPanel.setWidget(ownerSelectionView.asWidget());
    }

    @Override
    public void setName(final String name) {
        nameTextBox.setText(name);
    }

    @Override
    public void setPrefix(final String prefix) {
        prefixTextBox.setText(prefix);
    }

    @Override
    public String getName() {
        return nameTextBox.getText();
    }

    @Override
    public void setApiKey(final String apiKey) {
        apiKeyTextArea.setValue(apiKey);
    }

    @Override
    public void setComments(final String comments) {
        commentsTextArea.setText(comments);
    }

    @Override
    public String getComments() {
        return commentsTextArea.getText();
    }

    @Override
    public void setExpiresOn(final Long expiresOn) {
        expiresOnDateBox.setMilliseconds(expiresOn);
    }

    @Override
    public Long getExpiresOnMs() {
        return expiresOnDateBox.getMilliseconds();
    }

    @Override
    public void setEnabled(final boolean isEnabled) {
        enabledCheckBox.setValue(isEnabled);
    }

    @Override
    public boolean isEnabled() {
        return enabledCheckBox.getValue();
    }

    @Override
    public void focus() {
        Scheduler.get().scheduleDeferred(() -> {
            nameTextBox.setFocus(true);
        });
    }

    @Override
    public void reset(final Long milliseconds) {
        nameTextBox.setText("");
        commentsTextArea.setText("");
        expiresOnDateBox.setMilliseconds(milliseconds);
        enabledCheckBox.setValue(true);
        prefixTextBox.setText("");
        apiKeyTextArea.setText("");
    }

    @Override
    public void setHashAlgorithm(final HashAlgorithm hashAlgorithm) {
        hashAlgorithmSelectionBox.setValue(hashAlgorithm);
    }

    @Override
    public HashAlgorithm getHashAlgorithm() {
        return hashAlgorithmSelectionBox.getValue();
    }

    @Override
    public Widget asWidget() {
        return widget;
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, EditApiKeyViewImpl> {

    }
}

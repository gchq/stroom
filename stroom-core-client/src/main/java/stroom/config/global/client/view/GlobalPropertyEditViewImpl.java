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

package stroom.config.global.client.view;

import stroom.config.global.client.presenter.ManageGlobalPropertyEditPresenter.GlobalPropertyEditView;
import stroom.config.global.client.presenter.ManageGlobalPropertyEditUiHandlers;
import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;
import stroom.util.client.ClipboardUtil;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public final class GlobalPropertyEditViewImpl
        extends ViewWithUiHandlers<ManageGlobalPropertyEditUiHandlers>
        implements GlobalPropertyEditView {

    private final Widget widget;

    @UiField
    Label name;
    @UiField
    InlineSvgButton copyNameButton;
    @UiField
    TextArea description;
    @UiField
    TextArea defaultValue;
    @UiField
    TextArea yamlValue;
    @UiField
    CustomCheckBox useOverride;
    @UiField
    TextArea databaseValue;
    @UiField
    PasswordTextBox databaseValuePassword;
    @UiField
    TextArea effectiveValue;
    @UiField
    Label dataType;
    @UiField
    Label source;
    @UiField
    CustomCheckBox requireRestart;
    @UiField
    CustomCheckBox requireUiRestart;
    @UiField
    CustomCheckBox readOnly;
    // TODO This btn panel doesn't appear to be used. Probably not needed as the effectiveValueButtonPanel
    //  does the job of showing what values are effective on the nodes.
    @UiField
    ButtonPanel yamlValueButtonPanel;
    @UiField
    ButtonPanel effectiveValueButtonPanel;
    @UiField
    ButtonPanel dataTypeButtonPanel;

    private boolean password;

    @Inject
    public GlobalPropertyEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        setPasswordStyle(false);
        setEditable(false);

        description.setReadOnly(true);
        defaultValue.setReadOnly(true);
        yamlValue.setReadOnly(true);
        effectiveValue.setReadOnly(true);

        requireRestart.setEnabled(false);
        requireUiRestart.setEnabled(false);
        readOnly.setEnabled(false);

        useOverride.addValueChangeHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onChangeUseOverride();
            }
        });

        databaseValue.addValueChangeHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onChangeOverrideValue();
            }
        });

        databaseValuePassword.addValueChangeHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onChangeOverrideValue();
            }
        });

        copyNameButton.setSvg(SvgImage.COPY);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        useOverride.setFocus(true);
    }

    @Override
    public HasText getName() {
        return name;
    }

    @Override
    public HasText getDatabaseValue() {
        if (password) {
            return databaseValuePassword;
        } else {
            return databaseValue;
        }
    }

    @Override
    public HasText getDescription() {
        return description;
    }

    @Override
    public HasText getDefaultValue() {
        return defaultValue;
    }

    @Override
    public HasText getYamlValue() {
        return yamlValue;
    }

    @Override
    public HasText getEffectiveValue() {
        return effectiveValue;
    }

    @Override
    public HasText getDataType() {
        return dataType;
    }

    @Override
    public HasText getSource() {
        return source;
    }

    @Override
    public boolean getUseOverride() {
        return useOverride.getValue();
    }

    @Override
    public void setPasswordStyle(final boolean password) {
        this.password = password;

        databaseValue.setVisible(!password);
        databaseValuePassword.setVisible(password);
    }

    @Override
    public void setRequireRestart(final boolean val) {
        requireRestart.setValue(val);
    }

    @Override
    public void setRequireUiRestart(final boolean val) {
        requireUiRestart.setValue(val);
    }

    @Override
    public void setEditable(final boolean edit) {
        readOnly.setValue(!edit);

        databaseValue.setReadOnly(!edit);
        databaseValuePassword.setReadOnly(!edit);
        useOverride.setEnabled(edit);

        // disable the override fields if use override is not ticked
        if (edit) {
            databaseValue.setReadOnly(!useOverride.getValue());
            databaseValuePassword.setReadOnly(!useOverride.getValue());
        }
    }

    @Override
    public void setUseOverride(final boolean useOverride) {
        this.useOverride.setValue(useOverride);
    }

    @Override
    public ButtonView addYamlValueWarningIcon(final Preset preset) {
        return yamlValueButtonPanel.addButton(preset);
    }

    @Override
    public ButtonView addEffectiveValueIcon(final Preset preset) {
        return effectiveValueButtonPanel.addButton(preset);
    }

    @Override
    public ButtonView addDataTypeHelpIcon(final Preset preset) {
        return dataTypeButtonPanel.addButton(preset);
    }

    @UiHandler("copyNameButton")
    public void onCopyNameButton(final ClickEvent event) {
        ClipboardUtil.copy(name.getText());
    }

    public interface Binder extends UiBinder<Widget, GlobalPropertyEditViewImpl> {

    }
}

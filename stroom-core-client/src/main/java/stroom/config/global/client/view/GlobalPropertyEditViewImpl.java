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

package stroom.config.global.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.config.global.client.presenter.ManageGlobalPropertyEditPresenter.GlobalPropertyEditView;
import stroom.config.global.client.presenter.ManageGlobalPropertyEditUiHandlers;
import stroom.widget.tickbox.client.view.TickBox;

public final class GlobalPropertyEditViewImpl
        extends ViewWithUiHandlers<ManageGlobalPropertyEditUiHandlers>
        implements GlobalPropertyEditView {

    private final Widget widget;
    @UiField
    Grid grid;
    @UiField
    TextBox name;
    @UiField
    TextArea description;
    @UiField
    TextArea defaultValue;
    @UiField
    TextArea yamlValue;
    @UiField
    TickBox useOverride;
    @UiField
    TextArea databaseValue;
    @UiField
    PasswordTextBox databaseValuePassword;
    @UiField
    TextArea effectiveValue;
    @UiField
    TextBox dataType;
    @UiField
    TextBox source;
    @UiField
    TickBox requireRestart;
    @UiField
    TickBox requireUiRestart;
    @UiField
    TickBox readOnly;

    private boolean password;
//    private static volatile Resources RESOURCES;

    @Inject
    public GlobalPropertyEditViewImpl(final EventBus eventBus, final Binder binder) {
        widget = binder.createAndBindUi(this);
//        RESOURCES = GWT.create(Resources.class);
//        RESOURCES.style().ensureInjected();

        setPasswordStyle(false);
        setEditable(false);

        name.setReadOnly(true);
        description.setReadOnly(true);
        source.setReadOnly(true);
        defaultValue.setReadOnly(true);
        yamlValue.setReadOnly(true);
        effectiveValue.setReadOnly(true);
        dataType.setReadOnly(true);

        requireRestart.setEnabled(false);
        requireUiRestart.setEnabled(false);
        readOnly.setEnabled(false);

        useOverride.addValueChangeHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onChangeUseOverride();
            }
        });

        databaseValue.addKeyUpHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onChangeOverrideValue();
            }
        });

        databaseValuePassword.addKeyUpHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onChangeOverrideValue();
            }
        });
    }

    @Override
    public Widget asWidget() {
        return widget;
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
        return useOverride.getBooleanValue();
    }

    @Override
    public void setPasswordStyle(final boolean password) {
        this.password = password;

        grid.getRowFormatter().setVisible(4, !password);
        grid.getRowFormatter().setVisible(5, password);
    }

    @Override
    public void setRequireRestart(final boolean val) {
        requireRestart.setBooleanValue(val);
    }

    @Override
    public void setRequireUiRestart(final boolean val) {
        requireUiRestart.setBooleanValue(val);
    }

    @Override
    public void setEditable(final boolean edit) {
        readOnly.setBooleanValue(!edit);

        databaseValue.setReadOnly(!edit);
        databaseValuePassword.setReadOnly(!edit);
        useOverride.setEnabled(edit);

        // disable the override fields if use override is not ticked
        if (edit) {
            databaseValue.setReadOnly(!useOverride.getBooleanValue());
            databaseValuePassword.setReadOnly(!useOverride.getBooleanValue());
//            if (useOverride.getBooleanValue()) {
//                databaseValue.removeStyleName(RESOURCES.style().readonlyText());
//                databaseValuePassword.removeStyleName(RESOURCES.style().readonlyText());
//            } else {
//                databaseValue.addStyleName(RESOURCES.style().readonlyText());
//                databaseValuePassword.addStyleName(RESOURCES.style().readonlyText());
//            }
        }
//        if (edit) {
//            databaseValue.removeStyleName("never-editable");
//            databaseValuePassword.removeStyleName("never-editable");
//        } else {
//            databaseValue.setStyleName("never-editable");
//            databaseValuePassword.setStyleName("never-editable");
//        }
    }

    @Override
    public void setUseOverride(final boolean useOverride) {
        this.useOverride.setBooleanValue(useOverride);
    }

//    public interface Style extends CssResource {
//        String max();
//        String textArea();
//        String reducedHeight();
//        String readonlyText();
//    }
//
//    public interface Resources extends ClientBundle {
//        ImageResource filterActive();
//
//        ImageResource filterInactive();
//
//        @Source("globalpropertyedit.css")
//        GlobalPropertyEditViewImpl.Style style();
//    }

    public interface Binder extends UiBinder<Widget, GlobalPropertyEditViewImpl> {
    }
}

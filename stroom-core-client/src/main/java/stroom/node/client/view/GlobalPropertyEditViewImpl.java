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

package stroom.node.client.view;

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
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.node.client.presenter.ManageGlobalPropertyEditPresenter.GlobalPropertyEditView;
import stroom.widget.tickbox.client.view.TickBox;

public final class GlobalPropertyEditViewImpl extends ViewImpl implements GlobalPropertyEditView {
    private final Widget widget;
    @UiField
    Grid grid;
    @UiField
    TextBox name;
    @UiField
    TextArea description;
    @UiField
    TextArea value;
    @UiField
    TextBox source;
    @UiField
    TextBox defaultValue;
    @UiField
    PasswordTextBox valuePassword;
    @UiField
    TickBox requireRestart;
    @UiField
    TickBox requireUiRestart;
    private boolean password;

    @Inject
    public GlobalPropertyEditViewImpl(final EventBus eventBus, final Binder binder) {
        widget = binder.createAndBindUi(this);
        setPasswordStyle(false);
        setEditable(false);

        name.setReadOnly(true);
        description.setReadOnly(true);
        source.setReadOnly(true);
        defaultValue.setReadOnly(true);

        requireRestart.setEnabled(false);
        requireUiRestart.setEnabled(false);
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
    public HasText getValue() {
        if (password) {
            return valuePassword;
        } else {
            return value;
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
    public HasText getSource() {
        return source;
    }

    @Override
    public void setPasswordStyle(final boolean password) {
        this.password = password;

        grid.getRowFormatter().setVisible(2, !password);
        grid.getRowFormatter().setVisible(3, password);
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
        value.setReadOnly(!edit);
        valuePassword.setReadOnly(!edit);
    }

    public interface Binder extends UiBinder<Widget, GlobalPropertyEditViewImpl> {
    }
}

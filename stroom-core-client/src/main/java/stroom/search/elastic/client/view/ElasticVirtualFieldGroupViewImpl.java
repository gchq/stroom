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

package stroom.search.elastic.client.view;

import stroom.search.elastic.client.presenter.ElasticVirtualFieldDialogUiHandlers;
import stroom.search.elastic.client.presenter.ElasticVirtualFieldGroupPresenter.ElasticVirtualFieldGroupView;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.view.DialogAction;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

/// The view of the dialog for adding or editing a group of a virtual field.
public class ElasticVirtualFieldGroupViewImpl
        extends ViewWithUiHandlers<ElasticVirtualFieldDialogUiHandlers>
        implements ElasticVirtualFieldGroupView {

    private final Widget widget;

    @UiField
    TextBox name;
    @UiField
    Label nameError;
    @UiField
    SimplePanel expression;

    @Inject
    public ElasticVirtualFieldGroupViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getName() {
        return name.getValue();
    }

    @Override
    public void setName(final String name) {
        this.name.setValue(NullSafe.string(name));
    }

    @Override
    public void setNameError(final String error) {
        nameError.setText(NullSafe.string(error));
        nameError.setVisible(NullSafe.isNonBlankString(error));
    }

    @Override
    public void setNameEditable(final boolean editable) {
        name.setEnabled(editable);
    }

    @Override
    public void setExpressionView(final View view) {
        expression.setWidget(view.asWidget());
    }

    @Override
    public void focus() {
        if (name.isEnabled()) {
            name.setFocus(true);
            name.selectAll();
        }
    }

    @UiHandler("name")
    void onNameKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER && getUiHandlers() != null) {
            getUiHandlers().onDialogAction(DialogAction.OK);
        }
    }

    @UiHandler("name")
    void onNameKeyUp(final KeyUpEvent event) {
        if (event.getNativeKeyCode() != KeyCodes.KEY_ENTER && getUiHandlers() != null) {
            getUiHandlers().onNameEdit();
        }
    }

    /// UiBinder for [ElasticVirtualFieldGroupViewImpl].
    public interface Binder extends UiBinder<Widget, ElasticVirtualFieldGroupViewImpl> {

    }
}

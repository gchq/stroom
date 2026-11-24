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
import stroom.search.elastic.client.presenter.ElasticVirtualFieldNamePresenter.ElasticVirtualFieldNameView;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.view.DialogAction;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

/// The view of the dialog asking for the name of a virtual field.
public class ElasticVirtualFieldNameViewImpl
        extends ViewWithUiHandlers<ElasticVirtualFieldDialogUiHandlers>
        implements ElasticVirtualFieldNameView {

    private final Widget widget;

    @UiField
    TextBox name;
    @UiField
    Label nameError;

    @Inject
    public ElasticVirtualFieldNameViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        name.setFocus(true);
        name.selectAll();
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
    public void setError(final String error) {
        nameError.setText(NullSafe.string(error));
        nameError.setVisible(NullSafe.isNonBlankString(error));
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

    /// UiBinder for [ElasticVirtualFieldNameViewImpl].
    public interface Binder extends UiBinder<Widget, ElasticVirtualFieldNameViewImpl> {

    }
}

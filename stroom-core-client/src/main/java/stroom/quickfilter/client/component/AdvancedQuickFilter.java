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

package stroom.quickfilter.client.component;

import stroom.query.client.component.ExpressionEditor;
import stroom.widget.component.client.Component;
import stroom.widget.popup.client.PopupUtil;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A quick filter as an expression tree, below a message when its text couldn't be converted.
public class AdvancedQuickFilter extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Label message;
    @UiField
    ExpressionEditor expressionEditor;

    /// Makes the form, with no message.
    public AdvancedQuickFilter() {
        final Widget widget = BINDER.createAndBindUi(this);
        initWidget(widget);
        // The dialog is described by the message, if there is one, so that it is read as the
        // dialog opens (the focus goes to the expression)
        widget.addAttachHandler(event -> {
            if (event.isAttached()) {
                PopupUtil.setDialogDescription(widget.getElement(), message.isVisible()
                        ? message.getElement()
                        : null);
            }
        });
    }

    /// @return The expression's editor, which its controller attaches the editor's controller to.
    public ExpressionEditor getExpressionEditor() {
        return expressionEditor;
    }

    /// @param text A message shown above the tree, or `null` to hide it.
    public void setMessage(final String text) {
        message.setText(text == null
                ? ""
                : text);
        message.setVisible(text != null);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AdvancedQuickFilter> {

    }
}

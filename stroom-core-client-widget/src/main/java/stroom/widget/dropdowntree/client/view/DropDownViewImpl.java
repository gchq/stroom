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

package stroom.widget.dropdowntree.client.view;

import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.presenter.client.ViewWithUiHandlers;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

/// The view of a presenter that shows a picker ([DropDownBox]), e.g. `UserRefSelectionBoxPresenter`:
/// asking the picker to open asks the presenter to show its popup.
public class DropDownViewImpl extends ViewWithUiHandlers<DropDownUiHandlers>
        implements DropDownView {

    private final DropDownBox box = new DropDownBox();

    @Inject
    public DropDownViewImpl() {
        box.setOnOpen(() -> {
            if (getUiHandlers() != null) {
                getUiHandlers().showPopup();
            }
        });
    }

    @Override
    public Widget asWidget() {
        return box;
    }

    @Override
    public DropDownBox getBox() {
        return box;
    }

    @Override
    public void focus() {
        box.focus();
    }

    @Override
    public void setText(final String text) {
        box.setText(text);
    }

    @Override
    public void setProblem(final String message, final String fullMessage) {
        box.setProblem(message, fullMessage);
    }

    @Override
    public void setEnabled(final boolean enabled) {
        box.setEnabled(enabled);
    }

    @Override
    public void setReadOnly(final boolean readOnly) {
        box.setReadOnly(readOnly);
    }
}

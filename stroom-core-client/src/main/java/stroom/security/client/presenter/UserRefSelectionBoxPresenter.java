/*
 * Copyright 2024 Crown Copyright
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

package stroom.security.client.presenter;

import stroom.security.client.component.UserRefSelectionController;
import stroom.security.shared.FindUserContext;
import stroom.util.shared.UserRef;
import stroom.widget.dropdowntree.client.view.DropDownView;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.function.Consumer;

/// A user or group picker as a presenter, for the screens that still take presenters: a
/// [UserRefSelectionController] attached to the view's picker, which it owns. A screen split into a
/// component and a controller declares the picker (`DropDownBox`) in its template and attaches a
/// [UserRefSelectionController] itself.
public class UserRefSelectionBoxPresenter extends MyPresenterWidget<DropDownView> implements Focus {

    private final UserRefSelectionController controller;

    @Inject
    public UserRefSelectionBoxPresenter(final EventBus eventBus,
                                        final DropDownView view,
                                        final UserRefSelectionController controller) {
        super(eventBus, view);
        this.controller = own(controller).attach(view.getBox());
    }

    @Override
    public void focus() {
        controller.focus();
    }

    /// @see UserRefSelectionController#getSelected()
    public UserRef getSelected() {
        return controller.getSelected();
    }

    /// @see UserRefSelectionController#setSelected(UserRef)
    public void setSelected(final UserRef userRef) {
        controller.setSelected(userRef);
    }

    /// @see UserRefSelectionController#addSelectionListener(Consumer)
    public HandlerRegistration addSelectionListener(final Consumer<UserRef> listener) {
        return controller.addSelectionListener(listener);
    }

    /// @see UserRefSelectionController#setReadOnly(boolean)
    public void setReadOnly(final boolean readOnly) {
        controller.setReadOnly(readOnly);
    }

    /// @see UserRefSelectionController#setEnabled(boolean)
    public void setEnabled(final boolean enabled) {
        controller.setEnabled(enabled);
    }

    /// @see UserRefSelectionController#setContext(FindUserContext)
    public void setContext(final FindUserContext context) {
        controller.setContext(context);
    }
}

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

import stroom.security.shared.FindUserContext;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Listeners;
import stroom.widget.dropdowntree.client.view.DropDownUiHandlers;
import stroom.widget.dropdowntree.client.view.DropDownView;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.Objects;
import java.util.function.Consumer;

public class UserRefSelectionBoxPresenter extends MyPresenterWidget<DropDownView>
        implements DropDownUiHandlers, Focus {

    private final UserRefPopupPresenter userRefPopupPresenter;
    private boolean enabled = true;
    private boolean readOnly;

    private final Listeners<UserRef> selectionListeners = new Listeners<>();

    @Inject
    public UserRefSelectionBoxPresenter(final EventBus eventBus,
                                        final DropDownView view,
                                        final UserRefPopupPresenter userRefPopupPresenter) {
        super(eventBus, view);
        own(userRefPopupPresenter);
        view.setUiHandlers(this);
        this.userRefPopupPresenter = userRefPopupPresenter;
        userRefPopupPresenter.setSelectionChangeConsumer(this::changeSelection);
        changeSelection(null);
    }

    @Override
    public void focus() {
        getView().focus();
    }

    public UserRef getSelected() {
        return userRefPopupPresenter.getSelected();
    }

    public void setSelected(final UserRef userRef) {
        userRefPopupPresenter.setSelected(userRef);
        changeSelection(userRef);
    }

    @Override
    public void showPopup() {
        if (enabled && !readOnly) {
            final UserRef initialSelection = userRefPopupPresenter.getSelected();
            userRefPopupPresenter.show(userRef -> {
                final UserRef currentSelection = userRefPopupPresenter.getSelected();
                if (!Objects.equals(initialSelection, currentSelection)) {
                    selectionListeners.fire(userRef);
                }
            });
        }
    }

    /// Listens for the user choosing a user or group, until the registration is removed.
    ///
    /// @param listener Told what is selected.
    /// @return The registration, for the listener's owner to own.
    public HandlerRegistration addSelectionListener(final Consumer<UserRef> listener) {
        return selectionListeners.add(listener);
    }

    private void changeSelection(final UserRef selection) {
        if (selection == null) {
            getView().setText("None");
        } else {
            getView().setText(selection.toDisplayString());
        }
    }

    /// Makes the selection read only: it can be read and stays in the tab order, but the
    /// picker doesn't open. It looks like the normal field with its value greyed.
    ///
    /// @param readOnly Whether the selection is read only.
    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        getView().setReadOnly(readOnly);
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        getView().setEnabled(enabled);
    }

    public void setContext(final FindUserContext context) {
        userRefPopupPresenter.setContext(context);
    }
}

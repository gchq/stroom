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

package stroom.security.client.presenter;

import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.security.shared.FindUserContext;
import stroom.util.shared.UserRef;
import stroom.widget.dropdowntree.client.view.DropDownUiHandlers;
import stroom.widget.dropdowntree.client.view.DropDownView;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Objects;

public class UserRefSelectionBoxPresenter extends MyPresenterWidget<DropDownView>
        implements DropDownUiHandlers, HasDataSelectionHandlers<UserRef>, Focus {

    private final UserRefPopupPresenter userRefPopupPresenter;
    private boolean enabled = true;

    @Inject
    public UserRefSelectionBoxPresenter(final EventBus eventBus,
                                        final DropDownView view,
                                        final UserRefPopupPresenter userRefPopupPresenter) {
        super(eventBus, view);
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
        if (enabled) {
            final UserRef initialSelection = userRefPopupPresenter.getSelected();
            userRefPopupPresenter.show(userRef -> {
                final UserRef currentSelection = userRefPopupPresenter.getSelected();
                if (!Objects.equals(initialSelection, currentSelection)) {
                    DataSelectionEvent.fire(UserRefSelectionBoxPresenter.this, userRef, true);
                }
            });
        }
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<UserRef> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
    }

    private void changeSelection(final UserRef selection) {
        if (selection == null) {
            getView().setText("None", false);
        } else {
            getView().setText(selection.toDisplayString(), false);
        }
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            getView().asWidget().getElement().addClassName("disabled");
        } else {
            getView().asWidget().getElement().removeClassName("disabled");
        }
    }

    public void setContext(final FindUserContext context) {
        userRefPopupPresenter.setContext(context);
    }
}

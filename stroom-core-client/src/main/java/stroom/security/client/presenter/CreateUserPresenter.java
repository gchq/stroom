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

import stroom.docref.HasDisplayValue;
import stroom.security.client.presenter.CreateUserPresenter.CreateUserView;
import stroom.security.shared.User;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.popup.client.event.DialogEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CreateUserPresenter extends MyPresenterWidget<CreateUserView> implements CreateUserUiHandlers {

    private final CreateNewGroupPresenter createNewGroupPresenter;
    private final CreateExternalUserPresenter createNewUserPresenter;
    private final CreateMultipleUsersPresenter createMultipleUsersPresenter;
    private final UiConfigCache uiConfigCache;

    @Inject
    public CreateUserPresenter(final EventBus eventBus,
                               final CreateUserView view,
                               final CreateNewGroupPresenter createNewGroupPresenter,
                               final CreateExternalUserPresenter createNewUserPresenter,
                               final CreateMultipleUsersPresenter createMultipleUsersPresenter,
                               final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.createNewGroupPresenter = createNewGroupPresenter;
        this.createNewUserPresenter = createNewUserPresenter;
        this.createMultipleUsersPresenter = createMultipleUsersPresenter;
        this.uiConfigCache = uiConfigCache;
        view.setUiHandlers(this);
    }

    public void show(final Consumer<User> consumer, final boolean includeGroups) {
        if (includeGroups) {
            getView().setCreateTypesVisible(false);
            getView().setCreateTypes(List.of(CreateType.USER_GROUP));
            getView().setCreateType(CreateType.USER_GROUP);
            final PopupSize popupSize = PopupSize.resizableX();
            show("Add User Group", createNewGroupPresenter.getView(), consumer, popupSize);
        } else {
            getView().setCreateTypesVisible(true);
            final List<CreateType> createTypes = new ArrayList<>();
            final String label = "Add External User(s)";
            createTypes.add(CreateType.IDP_USER);
            createTypes.add(CreateType.MULTI_IDP_USERS);
            getView().setCreateTypes(createTypes);
            getView().setCreateType(createTypes.get(0));
            final PopupSize popupSize = PopupSize.resizable(600, 600);
            show(label, createNewUserPresenter.getView(), consumer, popupSize);
        }
    }

    private void show(final String caption,
                      final Focus initialView,
                      final Consumer<User> consumer,
                      final PopupSize popupSize) {
        onTypeChange();
        createNewGroupPresenter.getView().setName("");
        createNewUserPresenter.getView().clear();
        createMultipleUsersPresenter.getView().clear();

        createNewGroupPresenter.setDialogActionUiHandlers(e -> {
            DialogEvent.fire(this, this, e);
        });
        createNewUserPresenter.setDialogActionUiHandlers(e -> {
            DialogEvent.fire(this, this, e);
        });

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> {
                    initialView.focus();
                })
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        switch (getView().getCreateType()) {
                            case USER_GROUP: {
                                createNewGroupPresenter.create(consumer, e, this);
                                break;
                            }
                            case IDP_USER: {
                                createNewUserPresenter.create(consumer, e, this);
                                break;
                            }
                            case MULTI_IDP_USERS: {
                                createMultipleUsersPresenter.create(consumer, e, this);
                                break;
                            }
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    @Override
    public void onTypeChange() {
        switch (getView().getCreateType()) {
            case USER_GROUP: {
                getView().setTypeView(createNewGroupPresenter.getView());
                break;
            }
            case IDP_USER: {
                getView().setTypeView(createNewUserPresenter.getView());
                break;
            }
            case MULTI_IDP_USERS: {
                getView().setTypeView(createMultipleUsersPresenter.getView());
                break;
            }
        }
    }


    // --------------------------------------------------------------------------------


    public enum CreateType implements HasDisplayValue {

        USER_GROUP("Add User Group"),
        IDP_USER("Add Identity Provider User"),
        MULTI_IDP_USERS("Add Multiple Identity Provider Users");

        private final String displayValue;

        CreateType(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }

    }


    // --------------------------------------------------------------------------------


    public interface CreateUserView extends View, HasUiHandlers<CreateUserUiHandlers> {

        void setCreateTypesVisible(boolean visible);

        void setCreateTypes(List<CreateType> createTypes);

        void setCreateType(CreateType createType);

        CreateType getCreateType();

        void setTypeView(View view);
    }
}

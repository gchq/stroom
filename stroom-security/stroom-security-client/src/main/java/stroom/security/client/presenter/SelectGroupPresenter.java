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

package stroom.security.client.presenter;

import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

public class SelectGroupPresenter extends AbstractDataUserListPresenter {

    @Inject
    public SelectGroupPresenter(final EventBus eventBus,
                                final UserListView userListView,
                                final PagerView pagerView,
                                final RestFactory restFactory) {
        super(eventBus, userListView, pagerView, restFactory);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getSelectionModel().addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect()) {
                if (getFindUserCriteria() != null &&
                        getFindUserCriteria().getRelatedUser() == null) {
                    HidePopupRequestEvent.builder(this).fire();
                }
            }
        }));
    }

    public void show(final Consumer<User> groupConsumer) {
        final FindUserCriteria findUserCriteria = new FindUserCriteria();

        // If we are a group then get users and vice versa.
        findUserCriteria.setGroup(true);
        setup(findUserCriteria);

        final PopupSize popupSize = PopupSize.resizable(400, 400);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Choose Group To Add")
                .onShow(e -> getView().focus())
                .onHide(e -> {
                    if (e.isOk() && groupConsumer != null) {
                        final User selected = getSelectionModel().getSelected();
                        if (selected != null) {
                            groupConsumer.accept(selected);
                        }
                    }
                })
                .fire();
    }
}

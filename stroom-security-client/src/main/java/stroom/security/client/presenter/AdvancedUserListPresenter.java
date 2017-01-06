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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.data.grid.client.DoubleClickEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.StringCriteria;
import stroom.security.shared.FindUserCriteria;
import stroom.widget.popup.client.event.HidePopupEvent;

public class AdvancedUserListPresenter extends AbstractUserListPresenter {
    private final ClientDispatchAsync dispatcher;
    private UserRefDataProvider dataProvider;
    private FindUserCriteria findUserCriteria;

    @Inject
    public AdvancedUserListPresenter(final EventBus eventBus, final UserListView userListView,
                                     final ClientDispatchAsync dispatcher) {
        super(eventBus, userListView);
        this.dispatcher = dispatcher;
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getDataGridView().addDoubleClickHandler(new DoubleClickEvent.Handler() {
            @Override
            public void onDoubleClick(DoubleClickEvent event) {
                if (findUserCriteria != null && findUserCriteria.getRelatedUser() == null) {
                    HidePopupEvent.fire(AdvancedUserListPresenter.this, AdvancedUserListPresenter.this, false, true);
                }
            }
        }));
    }

    @Override
    public void changeNameFilter(String name) {
        if (findUserCriteria != null) {
            String filter = name;

            if (filter != null) {
                filter = filter.trim();
                if (filter.length() == 0) {
                    filter = null;
                }
            }

            if ((filter == null && findUserCriteria.getName() == null) || (filter != null && filter.equals(findUserCriteria.getName()))) {
                return;
            }

            findUserCriteria.getName().setString(filter);
            findUserCriteria.getName().setMatchStyle(StringCriteria.MatchStyle.WildStandAndEnd);

            dataProvider.refresh();
        }
    }

    public void setup(final FindUserCriteria findUserCriteria) {
        this.findUserCriteria = findUserCriteria;
        dataProvider = new UserRefDataProvider(dispatcher, getDataGridView());
        dataProvider.setCriteria(findUserCriteria);
        refresh();
    }

    public void refresh() {
        dataProvider.refresh();
    }
}

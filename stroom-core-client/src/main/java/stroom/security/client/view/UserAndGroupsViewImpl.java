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

package stroom.security.client.view;

import stroom.security.client.presenter.UserAndGroupsPresenter.UserAndGroupsView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public final class UserAndGroupsViewImpl extends ViewImpl implements UserAndGroupsView {

    private final Widget widget;

    @UiField
    SimplePanel userList;
    @UiField
    SimplePanel memberOf;
    @UiField
    SimplePanel membersIn;

    @Inject
    public UserAndGroupsViewImpl(final EventBus eventBus, final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setUserList(final View view) {
        this.userList.setWidget(view.asWidget());
    }

    @Override
    public void setMemberOfView(View view) {
        memberOf.setWidget(view.asWidget());
    }

    @Override
    public void setMembersInView(final View view) {
        membersIn.setWidget(view.asWidget());
    }

    @Override
    public void setMembersInVisible(final boolean visible) {
        membersIn.getElement().getStyle().setOpacity(visible
                ? 1
                : 0.4);
    }

    public interface Binder extends UiBinder<Widget, UserAndGroupsViewImpl> {

    }
}

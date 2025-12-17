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

package stroom.security.client.view;

import stroom.security.client.presenter.UserAndGroupsPresenter.UserAndGroupsView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.ThinSplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public final class UserAndGroupsViewImpl extends ViewImpl implements UserAndGroupsView {

    private final Widget widget;

    @UiField
    ThinSplitLayoutPanel outerSplitLayoutPanel;
    @UiField
    SimplePanel userList;

    @UiField
    ThinSplitLayoutPanel innerSplitLayoutPanel;
    @UiField
    SimplePanel parents;
    @UiField
    SimplePanel children;

    @Inject
    public UserAndGroupsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setUserListVisible(final boolean visible) {
        userList.setVisible(visible);
        outerSplitLayoutPanel.setWidgetHidden(userList.asWidget(), !visible);
        if (visible) {
            outerSplitLayoutPanel.setVSplits(0.5);
        } else {
            outerSplitLayoutPanel.setVSplits();
        }
        outerSplitLayoutPanel.onResize();
    }

    @Override
    public void setUserList(final View view) {
        this.userList.setWidget(view.asWidget());
    }

    @Override
    public void setParentsView(final View view) {
        parents.setWidget(view.asWidget());
    }

    @Override
    public void setParentsVisible(final boolean visible) {
        parents.getElement().getStyle().setOpacity(visible
                ? 1
                : 0.4);
    }

    @Override
    public void setChildrenView(final View view) {
        children.setWidget(view.asWidget());
    }

    @Override
    public void setChildrenVisible(final boolean visible) {
        children.setVisible(visible);
        innerSplitLayoutPanel.setWidgetHidden(children.asWidget(), !visible);
        if (visible) {
            innerSplitLayoutPanel.setHSplits(0.5);
        } else {
            innerSplitLayoutPanel.setHSplits();
        }
        innerSplitLayoutPanel.onResize();
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, UserAndGroupsViewImpl> {

    }
}

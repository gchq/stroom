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
import stroom.widget.layout.client.SplitPane;
import stroom.widget.presenter.client.ViewImpl;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public final class UserAndGroupsViewImpl extends ViewImpl implements UserAndGroupsView {

    private final Widget widget;

    @UiField
    SplitPane outerSplitPane;
    @UiField
    SimplePanel userList;

    @UiField
    SplitPane innerSplitPane;
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
        // The pane below takes the space
        outerSplitPane.setPaneHidden(userList, !visible);
    }

    @Override
    public void setUserList(final IsWidget view) {
        this.userList.setWidget(view.asWidget());
    }

    @Override
    public void setParentsView(final IsWidget view) {
        parents.setWidget(view.asWidget());
    }

    @Override
    public void setParentsEnabled(final boolean enabled) {
        setPaneEnabled(parents, enabled);
    }

    @Override
    public void setChildrenView(final IsWidget view) {
        children.setWidget(view.asWidget());
    }

    @Override
    public void setChildrenEnabled(final boolean enabled) {
        setPaneEnabled(children, enabled);
    }

    // A pane that waits for a selection is shown dimmed, saying 'No Selection', rather than hidden,
    // so the screen doesn't change shape. It is also inert: out of the tab order, unclickable and
    // hidden from screen readers, rather than only looking unavailable
    private static void setPaneEnabled(final Widget pane, final boolean enabled) {
        pane.getElement().getStyle().setOpacity(enabled
                ? 1
                : 0.4);
        if (enabled) {
            pane.getElement().removeAttribute("inert");
        } else {
            pane.getElement().setAttribute("inert", "");
        }
    }

    @Override
    public void setChildrenVisible(final boolean visible) {
        children.setVisible(visible);
        // The Member Of pane takes the space
        innerSplitPane.setPaneHidden(children, !visible);
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, UserAndGroupsViewImpl> {

    }
}

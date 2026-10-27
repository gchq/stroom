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

package stroom.security.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.layout.client.SplitPane;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// The User Groups screen: the users and groups above; below, the groups the selected one is a
/// member of beside the members of the selected group. Each is a list its controller shows here.
public class UserAndGroups extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

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

    /// Makes the screen, with no lists.
    public UserAndGroups() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param visible Whether the users and groups are shown; the panes below take the space when
    ///                they are not.
    public void setUserListVisible(final boolean visible) {
        userList.setVisible(visible);
        // The pane below takes the space
        outerSplitPane.setPaneHidden(userList, !visible);
    }

    /// @param view The users and groups.
    public void setUserList(final IsWidget view) {
        this.userList.setWidget(view);
    }

    /// @param view The groups the selected user or group is a member of.
    public void setParentsView(final IsWidget view) {
        parents.setWidget(view);
    }

    /// Enables the Member Of pane, or dims it and makes it inert while nothing is selected.
    ///
    /// @param enabled Whether the pane is enabled.
    public void setParentsEnabled(final boolean enabled) {
        setPaneEnabled(parents, enabled);
    }

    /// @param view The members of the selected group.
    public void setChildrenView(final IsWidget view) {
        children.setWidget(view);
    }

    /// Enables the members pane, or dims it and makes it inert while nothing is selected.
    ///
    /// @param enabled Whether the pane is enabled.
    public void setChildrenEnabled(final boolean enabled) {
        setPaneEnabled(children, enabled);
    }

    // A pane that waits for a selection is shown dimmed (user-and-groups.css), saying 'No
    // Selection', rather than hidden, so the screen doesn't change shape. It is also inert: out of
    // the tab order, unclickable and hidden from screen readers, rather than only looking
    // unavailable
    private static void setPaneEnabled(final Widget pane, final boolean enabled) {
        if (enabled) {
            pane.getElement().removeAttribute("inert");
        } else {
            pane.getElement().setAttribute("inert", "");
        }
    }

    /// @param visible Whether the members pane is shown; the Member Of pane takes the space when
    ///                it is not.
    public void setChildrenVisible(final boolean visible) {
        children.setVisible(visible);
        // The Member Of pane takes the space
        innerSplitPane.setPaneHidden(children, !visible);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, UserAndGroups> {

    }
}

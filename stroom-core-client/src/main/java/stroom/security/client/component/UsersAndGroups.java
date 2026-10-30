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
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// The User Groups screen: the users and groups above; below, the groups the selected one is a
/// member of beside the members of the selected group. Each is a list its controller
/// attaches a list's controller to.
public class UsersAndGroups extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SplitPane outerSplitPane;
    @UiField
    SimplePanel userListPane;
    @UiField
    UserList userList;

    @UiField
    SplitPane innerSplitPane;
    @UiField
    SimplePanel parentsPane;
    @UiField
    UserList parents;
    @UiField
    SimplePanel childrenPane;
    @UiField
    UserList children;

    /// Makes the screen, with its lists empty.
    public UsersAndGroups() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param visible Whether the users and groups are shown; the panes below take the space when
    ///                they are not.
    public void setUserListVisible(final boolean visible) {
        userListPane.setVisible(visible);
        // The pane below takes the space
        outerSplitPane.setPaneHidden(userListPane, !visible);
    }

    /// @return The users and groups.
    public UserList getUserList() {
        return userList;
    }

    /// @return The groups the selected user or group is a member of.
    public UserList getParents() {
        return parents;
    }

    /// Enables the Member Of pane, or dims it and makes it inert while nothing is selected.
    ///
    /// @param enabled Whether the pane is enabled.
    public void setParentsEnabled(final boolean enabled) {
        setPaneEnabled(parentsPane, enabled);
    }

    /// @return The members of the selected group.
    public UserList getChildren() {
        return children;
    }

    /// Enables the members pane, or dims it and makes it inert while nothing is selected.
    ///
    /// @param enabled Whether the pane is enabled.
    public void setChildrenEnabled(final boolean enabled) {
        setPaneEnabled(childrenPane, enabled);
    }

    // A pane that waits for a selection is shown dimmed (users-and-groups.css), saying 'No
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
        childrenPane.setVisible(visible);
        // The Member Of pane takes the space
        innerSplitPane.setPaneHidden(childrenPane, !visible);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, UsersAndGroups> {

    }
}

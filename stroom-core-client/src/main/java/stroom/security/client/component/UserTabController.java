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

package stroom.security.client.component;

import stroom.content.client.component.ContentTabController;
import stroom.security.client.UserTabPlugin;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.UserRef;
import stroom.util.shared.string.CaseType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// A user's or group's tab: their identity, groups, permissions, dependencies and API keys, a tab
/// each ([UserTabs]).
public class UserTabController extends ContentTabController {

    public static final String TAB_TYPE = "User";

    private final UserTabs tabs;
    private UserRef userRef;
    private String label;

    /// Made by GIN, with its tabs.
    ///
    /// @param eventBus The application's event bus.
    /// @param tabs     The user's or group's tabs.
    @Inject
    public UserTabController(final EventBus eventBus, final UserTabs tabs) {
        super(eventBus);
        this.tabs = own(tabs);
        initWidget(tabs.asWidget());
    }

    @Override
    public SvgImage getIcon() {
        return userRef != null && userRef.isGroup()
                ? UserTabPlugin.GROUP_ICON
                : UserTabPlugin.USER_ICON;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    private static String getLabel(final UserRef userRef) {
        // A user with nothing to identify it would otherwise be shown as '{null}'
        if (userRef == null
            || (userRef.getDisplayName() == null
                && userRef.getSubjectId() == null
                && userRef.getFullName() == null
                && userRef.getUuid() == null)) {
            return "Unknown User/Group";
        }
        return userRef.getType(CaseType.SENTENCE) + ": " + userRef.toDisplayString();
    }

    /// Shows a user or group.
    ///
    /// @param userRef The user or group.
    public void setUserRef(final UserRef userRef) {
        this.userRef = userRef;
        this.label = getLabel(userRef);
        // A user isn't a document, so has no reference
        tabs.read(null, userRef);
    }

    /// @return The user or group shown.
    public UserRef getUserRef() {
        return userRef;
    }
}

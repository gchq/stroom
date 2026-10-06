/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.client.widgets.cellrenderers;

import stroom.data.client.presenter.UserRefCell;
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.app.screen.StorySecurityContext;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.client.event.OpenUserEvent;
import stroom.util.shared.UserRef;
import stroom.util.shared.UserRef.DisplayType;

import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.user.cellview.client.CellWidget;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;

/// Stories for Stroom's [UserRefCell], matching `Widgets/Cell Renderers/UserRefCell` in the React
/// Storybook. Each cell is a `CellWidget` in React's `CellBox` (see [CellRendererWidgets]).
///
/// React's `canOpen` prop is Stroom's permission check: the cell shows the open button for the
/// current user, or to a user with the 'Manage Users' permission. The stories give the cell a
/// [StorySecurityContext] with no app permissions, whose current user is the user React lets open
/// (or nobody). React's `onOpen` is Stroom's [OpenUserEvent], which the cell fires on its event
/// bus. React's `onCopy` has no GWT equivalent: the cell copies to the clipboard itself, so the
/// story reports a mouse down on the copy button.
public final class UserRefCellStories {

    private static final String ON_OPEN = "onOpen";
    private static final String ON_COPY = "onCopy";
    // The class of the cell's copy button
    private static final String COPY_CLASS_NAME = "userRefLinkCopy";
    private static final int CELL_WIDTH_PX = 260;

    private static final UserRef ALICE = UserRef.builder()
            .uuid("uuid-alice")
            .subjectId("alice")
            .displayName("Alice Anderson")
            .fullName("Alice J. Anderson")
            .user()
            .enabled()
            .build();
    private static final UserRef ADMINS = UserRef.builder()
            .uuid("uuid-admins")
            .subjectId("Administrators")
            .displayName("Administrators")
            .group()
            .enabled()
            .build();
    private static final UserRef DISABLED_USER = UserRef.builder()
            .uuid("uuid-bob")
            .subjectId("bob")
            .displayName("Bob (disabled)")
            .user()
            .disabled()
            .build();

    private UserRefCellStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Cell Renderers/UserRefCell", UserRefCellStories.class)
                .layout(StoryLayout.CENTERED)
                // No args: React's meta only sets `userRef: null`, and every story has its own render
                // A named user, a group, and a disabled user. Hover a row to reveal the copy / open
                // buttons
                .story("Basic", UserRefCellStories::basic)
                // DisplayType variants for the same user ref
                .story("DisplayTypes", context -> StoryPanels.column(8,
                        cellBox(cell(new SimpleEventBus(), null, true, DisplayType.AUTO), ALICE),
                        cellBox(cell(new SimpleEventBus(), null, true, DisplayType.SUBJECT_ID), ALICE),
                        cellBox(cell(new SimpleEventBus(), null, true, DisplayType.FULL_NAME), ALICE),
                        cellBox(cell(new SimpleEventBus(), null, true, DisplayType.UUID), ALICE)))
                // No icon, and an empty cell when there is no user ref
                .story("IconAndEmpty", context -> StoryPanels.column(8,
                        cellBox(cell(new SimpleEventBus(), null, false, DisplayType.AUTO), ALICE),
                        cellBox(cell(new SimpleEventBus(), null, true, DisplayType.AUTO), null)));
    }

    private static Widget basic(final StoryContext context) {
        final InlineLabel lastAction = CellRendererWidgets.lastAction();
        final Spy onOpen = context.fn(ON_OPEN);
        final Spy onCopy = context.fn(ON_COPY);
        final EventBus eventBus = new SimpleEventBus();
        eventBus.addHandler(OpenUserEvent.getType(), event -> {
            onOpen.call(event.getUserRef().getSubjectId());
            lastAction.setText("Last action: open " + event.getUserRef().getSubjectId());
        });

        // React's canOpen: only Alice can be opened, as she is the current user
        final UserRefCell<UserRef> cell = cell(eventBus, ALICE, true, DisplayType.AUTO);
        final FlowPanel column = StoryPanels.column(8);
        for (final UserRef userRef : new UserRef[]{ALICE, ADMINS, DISABLED_USER}) {
            final FlowPanel box = cellBox(cell, userRef);
            // Differs from React: there is no onCopy callback (the cell copies to the clipboard), so the
            // story reports the mouse down that the cell copies on
            box.addDomHandler(event -> {
                if (CellRendererWidgets.targetHasClassName(event.getNativeEvent(), COPY_CLASS_NAME)) {
                    final String text = userRef.toDisplayString(DisplayType.AUTO);
                    onCopy.call(text);
                    lastAction.setText("Last action: copy \"" + text + "\"");
                }
            }, MouseDownEvent.getType());
            column.add(box);
        }
        column.add(lastAction);
        return column;
    }

    /// The cell.
    ///
    /// @param currentUser The user the cell may open (React's `canOpen`), or null for none.
    private static UserRefCell<UserRef> cell(final EventBus eventBus,
                                             final UserRef currentUser,
                                             final boolean showIcon,
                                             final DisplayType displayType) {
        final StorySecurityContext securityContext = new StorySecurityContext()
                .setUser(currentUser)
                .setAppPermissions();
        return new UserRefCell.Builder<UserRef>()
                .eventBus(eventBus)
                .securityContext(securityContext)
                .showIcon(showIcon)
                .displayType(displayType)
                .userRefFunction(userRef -> userRef)
                .build();
    }

    private static FlowPanel cellBox(final UserRefCell<UserRef> cell, final UserRef userRef) {
        return CellRendererWidgets.cellBox(new CellWidget<>(cell, userRef), CELL_WIDTH_PX);
    }
}

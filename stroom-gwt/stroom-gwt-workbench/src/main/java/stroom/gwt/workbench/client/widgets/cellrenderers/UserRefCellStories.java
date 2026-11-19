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

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.client.cell.UserRefCell;
import stroom.util.shared.UserRef;
import stroom.util.shared.UserRef.DisplayType;
import stroom.widget.text.client.InlineLabel;

import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.user.cellview.client.CellWidget;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [UserRefCell]. Each cell is a `CellWidget` in a box (see
/// [CellRendererWidgets]).
///
/// Whether a user can be opened is the cell's `canOpen`: in Stroom, `RefCellsController` lets
/// those with the 'Manage Users' permission open any user, and anyone else only themself (see
/// `TestRefCellsController`). The stories stand in for someone with no app permissions: only the
/// user who is the current user (or nobody) can be opened. The `onOpen` spy reports the cell's
/// `onOpen` callback, which the controller sets to fire an `OpenUserEvent`. The cell copies to the
/// clipboard itself, so the story reports a mouse down on the copy button.
public final class UserRefCellStories {

    private static final String ON_OPEN = "onOpen";
    private static final String ON_COPY = "onCopy";
    // The class of the cell's copy button
    private static final String COPY_CLASS_NAME = "user-ref-link__copy";
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
                // No args: every story has its own render
                // A named user, a group, and a disabled user. Hover a row to reveal the copy / open
                // buttons
                .story("Basic", UserRefCellStories::basic)
                // Only the current user has an open button, which opens them
                .withPlay(play -> {
                    play.expect(play.queryByTitle("Open group Administrators")).toBeNull();
                    play.expect(play.queryByTitle("Open user Bob (disabled)")).toBeNull();
                    final Query open = play.getByRole("button", "Open user Alice Anderson");
                    play.fireEvent().mouseDown(open, EventInit.create().atCentreOf(open).buttons(1));
                    play.waitFor(() -> play.expect(play.spy(ON_OPEN)).toHaveBeenCalledWith("alice"));
                })
                // DisplayType variants for the same user ref
                .story("DisplayTypes", context -> StoryPanels.column(8,
                        cellBox(cell(null, true, DisplayType.AUTO).build(), ALICE),
                        cellBox(cell(null, true, DisplayType.SUBJECT_ID).build(), ALICE),
                        cellBox(cell(null, true, DisplayType.FULL_NAME).build(), ALICE),
                        cellBox(cell(null, true, DisplayType.UUID).build(), ALICE)))
                // No icon, and an empty cell when there is no user ref
                .story("IconAndEmpty", context -> StoryPanels.column(8,
                        cellBox(cell(null, false, DisplayType.AUTO).build(), ALICE),
                        cellBox(cell(null, true, DisplayType.AUTO).build(), null)));
    }

    private static Widget basic(final StoryContext context) {
        final InlineLabel lastAction = CellRendererWidgets.lastAction();
        final Spy onOpen = context.fn(ON_OPEN);
        final Spy onCopy = context.fn(ON_COPY);
        // Only Alice can be opened, as she is the current user
        final UserRefCell<UserRef> cell = cell(ALICE, true, DisplayType.AUTO)
                .onOpen(userRef -> {
                    onOpen.call(userRef.getSubjectId());
                    lastAction.setText("Last action: open " + userRef.getSubjectId());
                })
                .build();
        final FlowPanel column = StoryPanels.column(8);
        for (final UserRef userRef : new UserRef[]{ALICE, ADMINS, DISABLED_USER}) {
            final FlowPanel box = cellBox(cell, userRef);
            // The cell copies to the clipboard itself, so the story reports the mouse down that the
            // cell copies on
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

    /// The cell's builder, whose cell can open only the current user, as for someone with no app
    /// permissions, and reports nothing when it does.
    ///
    /// @param currentUser The user the cell may open, or null for none.
    private static UserRefCell.Builder<UserRef> cell(final UserRef currentUser,
                                                     final boolean showIcon,
                                                     final DisplayType displayType) {
        return new UserRefCell.Builder<UserRef>()
                .canOpen(userRef -> userRef.equals(currentUser))
                .onOpen(userRef -> {
                })
                .showIcon(showIcon)
                .displayType(displayType)
                .userRefFunction(userRef -> userRef);
    }

    private static FlowPanel cellBox(final UserRefCell<UserRef> cell, final UserRef userRef) {
        return CellRendererWidgets.cellBox(new CellWidget<>(cell, userRef), CELL_WIDTH_PX);
    }
}

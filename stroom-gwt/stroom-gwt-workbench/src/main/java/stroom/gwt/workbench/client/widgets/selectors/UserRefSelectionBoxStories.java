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


package stroom.gwt.workbench.client.widgets.selectors;

import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.client.component.UserRefSelectionController;
import stroom.util.shared.UserRef;
import stroom.widget.text.client.InlineLabel;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [UserRefSelectionController] attached to a `DropDownBox`: a drop-down
/// showing the chosen user or group, which opens Stroom's user picker. The users are found by a
/// [UserFixture].
public final class UserRefSelectionBoxStories {

    private static final String ON_CHANGE = "onChange";
    // Delays the users' search, to show the "Loading…" state
    private static final int FIND_DELAY_MILLIS = 150;
    private static final String SELECTED_ROW = "[role=dialog] .data-grid__row--selected";

    private UserRefSelectionBoxStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Selectors/UserRefSelectionBox", UserRefSelectionBoxStories.class)
                .layout(StoryLayout.CENTERED)
                // Nothing selected - shows "None". Click the box to open the picker, then click to
                // highlight and OK, or double-click, to choose
                .story("Unselected", context -> withEcho(context, null))
                // Pre-selected user - the box shows the chosen user's name
                .story("WithSelection", context -> withEcho(context,
                        UserFixture.fixtureUsers().get("u-alice")))
                // The dialog from the keyboard: Down from the filter goes to the chosen user and moving
                // chooses; Up from the first row goes back to the filter; typing in the grid goes to
                // the filter; Enter on a row chooses that row (even one only highlighted after
                // filtering) and closes the dialog
                .story("KeysInTheDialog", context -> withEcho(context,
                        UserFixture.fixtureUsers().get("u-alice")))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query picker = play.querySelector(".drop-down");
                    final String filter = "Filter users and groups";
                    play.tab();
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(screen.getByRole("searchbox", filter)).toHaveFocus());
                    play.expect(screen.getByRole("dialog")).toHaveAttribute("aria-modal", "true");
                    play.waitFor(() -> play.expect(screen.querySelector(SELECTED_ROW))
                            .toHaveTextContent("Alice Anderson"));
                    play.keyboard("{ArrowDown}");
                    play.expect(screen.getByRole("searchbox", filter)).not().toHaveFocus();
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(screen.querySelector(SELECTED_ROW)).toHaveTextContent("Bob Brown"));
                    play.waitFor(() -> play.expect(screen.getByText("Bob Brown chosen")).toBeInTheDocument());
                    play.keyboard("{ArrowUp}");
                    play.waitFor(() -> play.expect(screen.querySelector(SELECTED_ROW))
                            .toHaveTextContent("Alice Anderson"));
                    play.keyboard("{ArrowUp}");
                    play.expect(screen.getByRole("searchbox", filter)).toHaveFocus();
                    // Typed in the grid, the characters go to the filter
                    play.keyboard("{ArrowDown}");
                    play.keyboard("carol");
                    play.waitFor(() -> play.expect(screen.getByRole("searchbox", filter)).toHaveValue("carol"));
                    play.expect(screen.getByRole("searchbox", filter)).toHaveFocus();
                    play.waitFor(() -> play.expect(screen.queryByText("Bob Brown")).toBeNull());
                    // The list filtered by the whole text: Alice (shown for 'c' and 'ca') has gone too.
                    // Down too soon would go to a row of a list still being filtered
                    final Play dialog = screen.within(screen.getByRole("dialog"));
                    play.waitFor(() -> play.expect(dialog.queryByText("Alice Anderson")).toBeNull());
                    // Down goes to the first row (the chosen user isn't shown), which Enter chooses
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(screen.getByRole("searchbox", filter)).not().toHaveFocus());
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(screen.queryByRole("dialog")).toBeNull());
                    play.waitFor(() -> play.expect(play.getByText(TextMatch.startingWith("Selected: Carol")))
                            .toBeInTheDocument());
                    play.expect(picker).toHaveFocus();
                })
                // Disabled - the picker can't be opened
                .story("Disabled", context -> {
                    final ScreenHarness harness = harness(context);
                    final UserRefSelectionController box = box(context, harness);
                    box.setSelected(UserFixture.fixtureUsers().get("g-admins"));
                    box.setEnabled(false);
                    final FlowPanel frame = new FlowPanel();
                    frame.getElement().getStyle().setProperty("width", "320px");
                    frame.add(box.getBox());
                    harness.add(frame);
                    return harness.asWidget();
                });
    }

    /// The box with a `Selected: ...` echo below.
    private static Widget withEcho(final StoryContext context, final UserRef selected) {
        final ScreenHarness harness = harness(context);
        final UserRefSelectionController box = box(context, harness);
        final InlineLabel echo = new InlineLabel();
        echo.getElement().getStyle().setProperty("fontSize", "12px");
        if (selected != null) {
            box.setSelected(selected);
        }
        echo.setText("Selected: " + display(selected));
        harness.addRegistration(box.addSelectionListener(userRef ->
                echo.setText("Selected: " + display(userRef))));

        final FlowPanel frame = new FlowPanel();
        frame.getElement().getStyle().setProperty("width", "320px");
        frame.getElement().getStyle().setProperty("display", "flex");
        frame.getElement().getStyle().setProperty("flexDirection", "column");
        frame.getElement().getStyle().setProperty("gap", "8px");
        frame.add(box.getBox());
        frame.add(echo);
        harness.add(frame);
        return harness.asWidget();
    }

    private static String display(final UserRef user) {
        return user == null
                ? "(none)"
                : user.toDisplayString();
    }

    private static ScreenHarness harness(final StoryContext context) {
        final UserFixture users = UserFixture.fixtureUsers();
        return ScreenHarness.create(context, RestFixtures.builder()
                .post(UserFixture.FIND, request -> RestReply.json(users.find(request.getBody()))
                        .delayed(FIND_DELAY_MILLIS))
                // A filter used when the picker's dialog is closed is recorded in the history
                .post("/quickFilterHistory/v1/record", request -> RestReply.noContent())
                .build());
    }

    private static UserRefSelectionController box(final StoryContext context, final ScreenHarness harness) {
        final UserRefSelectionController box = SelectorWidgets.userRefSelectionBox(harness);
        harness.own(box);
        final Spy onChange = context.fn(ON_CHANGE);
        harness.addRegistration(box.addSelectionListener(selected -> onChange.call(selected == null
                ? null
                : selected.getUuid())));
        return box;
    }
}

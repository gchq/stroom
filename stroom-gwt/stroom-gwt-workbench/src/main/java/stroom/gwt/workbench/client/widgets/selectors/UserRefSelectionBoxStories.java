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
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.client.presenter.UserRefSelectionBoxPresenter;
import stroom.util.shared.UserRef;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [UserRefSelectionBoxPresenter], matching
/// `Widgets/Selectors/UserRefSelectionBox` in the React Storybook: a drop-down showing the chosen
/// user or group, which opens Stroom's user picker. The users are found by a [UserFixture] of the
/// React stories' users.
public final class UserRefSelectionBoxStories {

    private static final String ON_CHANGE = "onChange";
    // As the React fixture's loader, which shows the "Loading…" state
    private static final int FIND_DELAY_MILLIS = 150;

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
                // Disabled - the picker can't be opened
                .story("Disabled", context -> {
                    final ScreenHarness harness = harness(context);
                    final UserRefSelectionBoxPresenter box = box(context, harness);
                    box.setSelected(UserFixture.fixtureUsers().get("g-admins"));
                    box.setEnabled(false);
                    final FlowPanel frame = new FlowPanel();
                    frame.getElement().getStyle().setProperty("width", "320px");
                    frame.add(box.getWidget());
                    harness.add(frame);
                    return harness.asWidget();
                });
    }

    /// The React stories' box with a `Selected: ...` echo below.
    private static Widget withEcho(final StoryContext context, final UserRef selected) {
        final ScreenHarness harness = harness(context);
        final UserRefSelectionBoxPresenter box = box(context, harness);
        final InlineLabel echo = new InlineLabel();
        echo.getElement().getStyle().setProperty("fontSize", "12px");
        if (selected != null) {
            box.setSelected(selected);
        }
        echo.setText("Selected: " + display(selected));
        harness.addRegistration(box.addDataSelectionHandler(event ->
                echo.setText("Selected: " + display(event.getSelectedItem()))));

        final FlowPanel frame = new FlowPanel();
        frame.getElement().getStyle().setProperty("width", "320px");
        frame.getElement().getStyle().setProperty("display", "flex");
        frame.getElement().getStyle().setProperty("flexDirection", "column");
        frame.getElement().getStyle().setProperty("gap", "8px");
        frame.add(box.getWidget());
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
                .build());
    }

    private static UserRefSelectionBoxPresenter box(final StoryContext context, final ScreenHarness harness) {
        final UserRefSelectionBoxPresenter box = SelectorWidgets.userRefSelectionBox(harness);
        harness.unbindOnCleanUp(box);
        box.bind();
        final Spy onChange = context.fn(ON_CHANGE);
        harness.addRegistration(box.addDataSelectionHandler(event -> onChange.call(event.getSelectedItem() == null
                ? null
                : event.getSelectedItem().getUuid())));
        return box;
    }
}

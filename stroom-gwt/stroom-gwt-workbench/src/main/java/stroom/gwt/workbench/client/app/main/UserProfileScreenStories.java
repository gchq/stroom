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

package stroom.gwt.workbench.client.app.main;

import stroom.gwt.workbench.client.app.gin.security.SecurityScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.security.SecurityPlays;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.client.presenter.UserInfoPresenter;
import stroom.util.shared.UserRef;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/UserProfileScreen`, showing Stroom's real [UserInfoPresenter] (the 'Info'
/// sub-tab of a user's tab) for a user and a group. It makes no requests; the signed in user holds
/// no app permissions.
public final class UserProfileScreenStories {

    private static final UserRef ALICE = new UserRef("u-1", "alice@corp", "Alice Anderson",
            "Alice B. Anderson", false, true);
    private static final UserRef ADMINISTRATORS = new UserRef("g-1", "Administrators", "Administrators",
            null, true, true);

    private UserProfileScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/UserProfileScreen", UserProfileScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The current user's read only names and unique id
                .story("CurrentUser", context -> render(context, ALICE))
                .withPlay(play -> {
                    play.expect(play.findByDisplayValue("Alice Anderson")).toBeInTheDocument();
                    play.expect(play.getByDisplayValue("alice@corp")).toBeInTheDocument();
                    play.expect(play.getByLabelText("Unique Identifier")).toHaveAttribute("readonly", null);
                    SecurityPlays.expectNoProblems(play);
                })
                // A group: the names are hidden and the id is relabelled
                .story("Group", context -> render(context, ADMINISTRATORS))
                .withPlay(play -> {
                    play.findByLabelText("Group Name");
                    // GWT hides the Display Name form group rather than leaving it out, so its field is
                    // still in the document
                    play.expect(play.getByLabelText("Display Name")).not().toBeVisible();
                    SecurityPlays.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context, final UserRef userRef) {
        final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, RestFixtures.builder().build())
                .injector(injector)
                .user(ALICE)
                .appPermissions()
                .build();
        // Shown as UserTabPresenter shows its Info tab
        harness.afterStartUp(() -> {
            final UserInfoPresenter presenter = harness.addContent(injector.getUserInfoPresenter());
            presenter.setUserRef(userRef);
        });
        return harness.asWidget();
    }
}

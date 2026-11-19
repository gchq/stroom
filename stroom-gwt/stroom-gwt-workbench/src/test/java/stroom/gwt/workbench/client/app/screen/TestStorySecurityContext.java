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


package stroom.gwt.workbench.client.app.screen;

import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.rest.StartupFixtures;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestStorySecurityContext {

    private static final DocRef FEED = new DocRef("Feed", "f-1", "My Feed");
    private static final DocRef OTHER = new DocRef("Feed", "f-2", "Other Feed");

    @Test
    void testDefaults() {
        final StorySecurityContext context = StorySecurityContext.admin();

        assertThat(context.getUserRef()).isEqualTo(StartupFixtures.ADMIN);
        assertThat(context.isCurrentUser(StartupFixtures.ADMIN)).isTrue();
        assertThat(context.isLoggedIn()).isFalse();
        assertThat(context.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)).isTrue();
        assertThat(context.hasDocumentPermission(FEED, DocumentPermission.OWNER)).isTrue();
    }

    @Test
    void testAppPermissions() {
        final StorySecurityContext context = new StorySecurityContext()
                .setAppPermissions(AppPermission.MANAGE_USERS_PERMISSION);

        assertThat(context.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)).isTrue();
        assertThat(context.hasAppPermission(AppPermission.MANAGE_TASKS_PERMISSION)).isFalse();

        // None at all, as for an ordinary user
        context.setAppPermissions();
        assertThat(context.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)).isFalse();

        // ADMINISTRATOR implies the rest, as in Stroom's CurrentUser
        context.setAppPermissions(AppPermission.ADMINISTRATOR);
        assertThat(context.hasAppPermission(AppPermission.MANAGE_TASKS_PERMISSION)).isTrue();
    }

    @Test
    void testDocumentPermissions() {
        final StorySecurityContext context = new StorySecurityContext()
                .setDocumentPermission(DocumentPermission.VIEW)
                .setDocumentPermission(FEED, DocumentPermission.EDIT);

        assertThat(context.hasDocumentPermission(FEED, DocumentPermission.EDIT)).isTrue();
        assertThat(context.hasDocumentPermission(FEED, DocumentPermission.VIEW)).isTrue();
        assertThat(context.hasDocumentPermission(FEED, DocumentPermission.DELETE)).isFalse();
        assertThat(context.hasDocumentPermission(OTHER, DocumentPermission.VIEW)).isTrue();
        assertThat(context.hasDocumentPermission(OTHER, DocumentPermission.EDIT)).isFalse();

        context.setDocumentPermission(null);
        context.setDocumentPermission(FEED, null);
        assertThat(context.hasDocumentPermission(OTHER, DocumentPermission.USE)).isFalse();
        assertThat(context.hasDocumentPermission(FEED, DocumentPermission.USE)).isFalse();
    }

    @Test
    void testHasDocumentPermission_callback() {
        final StorySecurityContext context = new StorySecurityContext().setDocumentPermission(DocumentPermission.VIEW);
        final List<Boolean> answers = new ArrayList<>();

        context.hasDocumentPermission(FEED, DocumentPermission.VIEW, answers::add, e -> {
        }, null);
        context.hasDocumentPermission(FEED, DocumentPermission.EDIT, answers::add, e -> {
        }, null);

        assertThat(answers).containsExactly(true, false);
    }

    @Test
    void testUserAndLogIn() {
        final UserRef alice = new UserRef("u-1", "alice", "Alice", null, false, true);
        final StorySecurityContext context = new StorySecurityContext().setUser(alice).setLoggedIn(true);

        assertThat(context.getUserRef()).isEqualTo(alice);
        assertThat(context.isCurrentUser(StartupFixtures.ADMIN)).isFalse();
        assertThat(context.isLoggedIn()).isTrue();

        // Regression: UiConfigCache's timer of an old rendering refreshes while logged in
        context.dispose();
        assertThat(context.isLoggedIn()).isFalse();
    }
}

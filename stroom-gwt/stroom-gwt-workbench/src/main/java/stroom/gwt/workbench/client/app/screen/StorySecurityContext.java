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
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.UserRef;

import java.util.Objects;
import java.util.function.Consumer;

/// A [ClientSecurityContext] for screen stories: a user with every permission, so that screens
/// show all their controls, without asking the server.
public final class StorySecurityContext implements ClientSecurityContext {

    private final UserRef userRef;
    private final boolean loggedIn;

    private StorySecurityContext(final UserRef userRef, final boolean loggedIn) {
        this.userRef = userRef;
        this.loggedIn = loggedIn;
    }

    /// The `admin` user, who has every permission. Not logged in, so that caches such as
    /// `UiConfigCache` don't keep refreshing (and so making requests) while the story is shown.
    ///
    /// @return The security context.
    public static StorySecurityContext admin() {
        return new StorySecurityContext(
                new UserRef("admin-uuid", "admin", "admin", "Administrator", false, true),
                false);
    }

    @Override
    public UserRef getUserRef() {
        return userRef;
    }

    @Override
    public boolean isCurrentUser(final UserRef userRef) {
        return Objects.equals(this.userRef, userRef);
    }

    @Override
    public boolean isLoggedIn() {
        return loggedIn;
    }

    @Override
    public boolean hasAppPermission(final AppPermission permission) {
        return true;
    }

    @Override
    public void hasDocumentPermission(final DocRef docRef,
                                      final DocumentPermission permission,
                                      final Consumer<Boolean> consumer,
                                      final Consumer<Throwable> errorHandler,
                                      final TaskMonitorFactory taskMonitorFactory) {
        consumer.accept(true);
    }
}

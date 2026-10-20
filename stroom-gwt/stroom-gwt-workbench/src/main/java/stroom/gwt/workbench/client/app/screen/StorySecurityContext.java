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
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.UserRef;

import com.google.inject.Inject;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// The [ClientSecurityContext] of a screen story, bound in the harness's injector in place of
/// Stroom's `CurrentUser`: a configurable user with app and document permissions, answered
/// without asking the server. By default it is the `admin` user with the `ADMINISTRATOR`
/// permission (which, as in Stroom, implies every app permission) and the `OWNER` permission on
/// every document, so that screens show all their controls.
///
/// Configure it before creating the screen, e.g.
/// ```
/// harness.getSecurityContext()
///         .setUser(ALICE)
///         .setAppPermissions(AppPermission.MANAGE_USERS_PERMISSION)
///         .setDocumentPermission(DocumentPermission.VIEW)
///         .setDocumentPermission(FEED_REF, DocumentPermission.EDIT);
/// ```
/// (the harness builder's `user` and `appPermissions` set both this and the start-up fixtures).
///
/// It is not logged in by default, so that caches such as `UiConfigCache` don't keep refreshing
/// (and so making requests) while the story is shown, and it reports not being logged in once
/// the harness is disposed.
public final class StorySecurityContext implements ClientSecurityContext {

    private UserRef userRef = StartupFixtures.ADMIN;
    private final Set<AppPermission> appPermissions = new HashSet<>();
    private DocumentPermission defaultDocumentPermission = DocumentPermission.OWNER;
    private final Map<String, DocumentPermission> documentPermissions = new HashMap<>();
    private boolean loggedIn;
    private boolean disposed;

    /// Creates the default context, see the class description.
    @Inject
    public StorySecurityContext() {
        appPermissions.add(AppPermission.ADMINISTRATOR);
    }

    /// The `admin` user, who has every permission.
    ///
    /// @return A new security context.
    public static StorySecurityContext admin() {
        return new StorySecurityContext();
    }

    /// @param userRef The current user.
    /// @return This context.
    public StorySecurityContext setUser(final UserRef userRef) {
        this.userRef = userRef;
        return this;
    }

    /// @param permissions The current user's app permissions, replacing any set before. None
    ///                    for an ordinary user; `ADMINISTRATOR` implies all of them.
    /// @return This context.
    public StorySecurityContext setAppPermissions(final AppPermission... permissions) {
        return setAppPermissions(Arrays.asList(permissions));
    }

    /// @param permissions The current user's app permissions, replacing any set before.
    /// @return This context.
    public StorySecurityContext setAppPermissions(final Collection<AppPermission> permissions) {
        appPermissions.clear();
        appPermissions.addAll(permissions);
        return this;
    }

    /// @param permission The highest permission the user has on documents without their own
    ///                   permission set, e.g. `VIEW` for read-only screens, or null for none.
    /// @return This context.
    public StorySecurityContext setDocumentPermission(final DocumentPermission permission) {
        this.defaultDocumentPermission = permission;
        return this;
    }

    /// @param docRef     A document.
    /// @param permission The highest permission the user has on it, or null for none.
    /// @return This context.
    public StorySecurityContext setDocumentPermission(final DocRef docRef, final DocumentPermission permission) {
        documentPermissions.put(Objects.requireNonNull(docRef).getUuid(), permission);
        return this;
    }

    /// @param loggedIn Whether the user is logged in. Note that `UiConfigCache` then refreshes
    ///                 the UI config every minute.
    /// @return This context.
    public StorySecurityContext setLoggedIn(final boolean loggedIn) {
        this.loggedIn = loggedIn;
        return this;
    }

    /// Makes the context report not being logged in, so that timers of the old rendering (e.g.
    /// `UiConfigCache`'s) stop refreshing. Called by the harness when the story renders again.
    public void dispose() {
        disposed = true;
    }

    /// @return The current user.
    @Override
    public UserRef getUserRef() {
        return userRef;
    }

    /// @param userRef A user.
    /// @return True if it is the current user.
    @Override
    public boolean isCurrentUser(final UserRef userRef) {
        return Objects.equals(this.userRef, userRef);
    }

    /// @return True if set logged in and not disposed.
    @Override
    public boolean isLoggedIn() {
        return loggedIn && !disposed;
    }

    /// @param permission An app permission.
    /// @return True if the user has it, or is an administrator.
    @Override
    public boolean hasAppPermission(final AppPermission permission) {
        return appPermissions.contains(permission) || appPermissions.contains(AppPermission.ADMINISTRATOR);
    }

    /// Answers immediately (Stroom's `CurrentUser` asks the server) whether the user's highest
    /// permission on the document is the permission or a higher one.
    ///
    /// @param docRef             The document.
    /// @param permission         The permission.
    /// @param consumer           Given the answer.
    /// @param errorHandler       Not used.
    /// @param taskMonitorFactory Not used.
    @Override
    public void hasDocumentPermission(final DocRef docRef,
                                      final DocumentPermission permission,
                                      final Consumer<Boolean> consumer,
                                      final Consumer<Throwable> errorHandler,
                                      final TaskMonitorFactory taskMonitorFactory) {
        consumer.accept(hasDocumentPermission(docRef, permission));
    }

    /// @param docRef     The document.
    /// @param permission The permission.
    /// @return True if the user's highest permission on the document is the permission or a
    /// higher one.
    public boolean hasDocumentPermission(final DocRef docRef, final DocumentPermission permission) {
        final DocumentPermission highest = docRef != null && documentPermissions.containsKey(docRef.getUuid())
                ? documentPermissions.get(docRef.getUuid())
                : defaultDocumentPermission;
        return highest != null && permission != null && highest.isEqualOrHigher(permission);
    }
}

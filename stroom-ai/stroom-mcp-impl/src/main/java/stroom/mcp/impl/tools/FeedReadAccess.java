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

package stroom.mcp.impl.tools;

import stroom.docref.DocRef;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.security.shared.PermissionShowLevel;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/// Who may read each feed's data, as Stroom's document permissions on the feeds give it: the users and groups with
/// View (or more) on each feed. Compared before a build of several feeds makes its index (decided by the user,
/// 2026-11-25): one shared Elastic index gives everyone who can use it every feed's events, so where the feeds'
/// readers differ one index per feed is proposed. Only a user who may see a feed's permissions (its owner, or one
/// with Manage Users) reads them; for others the feed's readers aren't known, and the feeds are taken to differ.
class FeedReadAccess {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FeedReadAccess.class);

    private final Provider<DocumentPermissionService> permissionsProvider;
    private final SecurityContext securityContext;
    private final FeedNames feedNames;

    /// @param permissionsProvider Lists a document's permissions.
    /// @param securityContext     Says whether the user may see them.
    /// @param feedNames           Finds a feed by its name.
    @Inject
    FeedReadAccess(final Provider<DocumentPermissionService> permissionsProvider,
                   final SecurityContext securityContext,
                   final FeedNames feedNames) {
        this.permissionsProvider = permissionsProvider;
        this.securityContext = securityContext;
        this.feedNames = feedNames;
    }

    /// @param feeds Feeds' names.
    /// @return Who may read each, and the feeds whose readers can't be told.
    Access access(final List<String> feeds) {
        final Map<String, Set<String>> readers = new LinkedHashMap<>();
        final List<String> unknown = new ArrayList<>();
        for (final String feed : feeds) {
            final Optional<Set<String>> found = readers(feed);
            if (found.isPresent()) {
                readers.put(feed, found.get());
            } else {
                unknown.add(feed);
            }
        }
        return new Access(readers, unknown);
    }

    // The users and groups with View or more on the feed, by display name; empty when they can't be read.
    private Optional<Set<String>> readers(final String feed) {
        try {
            final Optional<DocRef> ref = feedNames.exact(feed);
            if (ref.isEmpty() || !(securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)
                                   || securityContext.hasDocumentPermission(ref.get(), DocumentPermission.OWNER))) {
                return Optional.empty();
            }
            final ResultPage<DocumentUserPermissions> page = permissionsProvider.get().fetchDocumentUserPermissions(
                    new FetchDocumentUserPermissionsRequest.Builder()
                            .docRef(ref.get())
                            .showLevel(PermissionShowLevel.SHOW_EXPLICIT)
                            .build());
            final Set<String> out = new TreeSet<>();
            for (final DocumentUserPermissions permissions : NullSafe.list(NullSafe.get(page,
                    ResultPage::getValues))) {
                if (permissions.getPermission() != null
                    && permissions.getPermission().isEqualOrHigher(DocumentPermission.VIEW)) {
                    out.add(NullSafe.get(permissions.getUserRef(), UserRef::toDisplayString));
                }
            }
            return Optional.of(out);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("The readers of feed {} weren't read: {}", feed, e.getMessage()), e);
            return Optional.empty();
        }
    }


    // --------------------------------------------------------------------------------


    /// Who may read each feed.
    ///
    /// @param readers Each feed's users and groups with View or more, where they could be read.
    /// @param unknown The feeds whose readers couldn't be read (the user may not see their permissions).
    record Access(Map<String, Set<String>> readers, List<String> unknown) {

        /// @return Whether every feed has the same readers, as far as can be told: false where any can't be read.
        boolean same() {
            return unknown.isEmpty() && new LinkedHashSet<>(readers.values()).size() <= 1;
        }

        /// @return It in words, for the user: each feed's readers, and those that couldn't be read.
        String said() {
            final List<String> each = new ArrayList<>();
            readers.forEach((feed, who) -> each.add(feed + ": " + (who.isEmpty()
                    ? "no one named on the feed"
                    : String.join(", ", who))));
            if (!unknown.isEmpty()) {
                each.add(String.join(", ", unknown) + ": not known (their permissions can't be seen as this user)");
            }
            return String.join("; ", each);
        }
    }
}

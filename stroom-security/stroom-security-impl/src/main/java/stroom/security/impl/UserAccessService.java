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

package stroom.security.impl;

import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.openid.api.TokenInventory;
import stroom.security.openid.api.TokenSummary;
import stroom.security.shared.AppPermission;
import stroom.security.shared.FindUserAccessCriteria;
import stroom.security.shared.SessionDetails;
import stroom.security.shared.User;
import stroom.security.shared.UserAccessRow;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Builds the administrative view of who holds live access, by merging two sources that no single query could
 * span: per-node in-memory sessions (this module) and the internal IdP's token table (across the seam).
 * <p>
 * The join is an <b>outer</b> join in both directions, and both directions matter:
 * </p>
 * <ul>
 *     <li><b>Sessions with no tokens</b> is the ordinary external-IdP row - nothing is minted here.</li>
 *     <li><b>Tokens with no session</b> is the case that forced this screen to be keyed on the subject: a closed
 *     browser, a {@code client_credentials} grant, or any API client that never had a session. Dropping these
 *     would leave those subjects invisible, and so unrevokable.</li>
 * </ul>
 */
@Singleton
public class UserAccessService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserAccessService.class);

    private final Provider<SessionListService> sessionListServiceProvider;
    private final Provider<TokenInventory> tokenInventoryProvider;
    private final Provider<UserService> userServiceProvider;
    private final SecurityContext securityContext;

    @Inject
    UserAccessService(final Provider<SessionListService> sessionListServiceProvider,
                      final Provider<TokenInventory> tokenInventoryProvider,
                      final Provider<UserService> userServiceProvider,
                      final SecurityContext securityContext) {
        this.sessionListServiceProvider = sessionListServiceProvider;
        this.tokenInventoryProvider = tokenInventoryProvider;
        this.userServiceProvider = userServiceProvider;
        this.securityContext = securityContext;
    }

    public ResultPage<UserAccessRow> find(final FindUserAccessCriteria criteria) {
        return securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, () -> {
            final List<UserAccessRow> rows = buildRows();
            final List<UserAccessRow> filtered = rows.stream()
                    .filter(activeFilter(criteria))
                    .filter(textFilter(criteria))
                    .sorted(comparator())
                    .toList();
            LOGGER.debug(() -> "Built " + rows.size() + " user access row(s), " + filtered.size()
                               + " after filtering");
            return ResultPage.createPageLimitedList(filtered, criteria.getPageRequest());
        });
    }

    /**
     * The sessions belonging to one subject.
     * <p>
     * Filtered here rather than in the client so that a caller only ever receives the sessions they asked about.
     * Each carries an opaque handle, never a session id - see {@link SessionDetails#getSessionHandle()}.
     * </p>
     */
    public List<SessionDetails> listSessions(final String subjectId) {
        if (NullSafe.isBlankString(subjectId)) {
            return List.of();
        }
        return securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, () ->
                sessionListServiceProvider.get().listSessions().getValues().stream()
                        .filter(details -> Objects.equals(
                                subjectId, NullSafe.get(details.getUserRef(), UserRef::getSubjectId)))
                        .sorted(Comparator.comparing(SessionDetails::getLastAccessedMs).reversed())
                        .toList());
    }

    /**
     * Merge the two sources into one row per subject.
     * <p>
     * <b>Scale.</b> Both sides are fetched in full and joined in memory. Sessions are bounded by concurrent
     * users; tokens are bounded by users active within the refresh token lifetime - 30 days by default - which
     * makes the token side potentially much larger, since it includes everyone who signed in this month rather
     * than only who is online now. For expected deployments that is thousands of rows and in-memory is right. If
     * it ever stops being right, the fix is a subject-id pre-filter pushed into
     * {@link TokenInventory}; name matching cannot be pushed down without joining the user table.
     * </p>
     */
    private List<UserAccessRow> buildRows() {
        final Map<String, SessionAggregate> sessionsBySubject = aggregateSessions();
        final Map<String, TokenSummary> tokensBySubject = tokenInventoryProvider.get()
                .summariseUsableTokensBySubject();

        // Union of both key sets, so neither side can hide a subject from the other.
        final Set<String> subjectIds = new LinkedHashSet<>(sessionsBySubject.keySet());
        subjectIds.addAll(tokensBySubject.keySet());

        final List<UserAccessRow> rows = new ArrayList<>(subjectIds.size());
        for (final String subjectId : subjectIds) {
            final SessionAggregate sessions = sessionsBySubject.get(subjectId);
            final TokenSummary tokens = tokensBySubject.getOrDefault(subjectId, TokenSummary.NONE);

            // Sessions already carry a UserRef; token rows carry only a subject id, so those have to be
            // resolved or the row would show an opaque id and be unfindable by name.
            final UserRef userRef = sessions != null && sessions.userRef != null
                    ? sessions.userRef
                    : resolveUserRef(subjectId);

            rows.add(new UserAccessRow(
                    subjectId,
                    userRef,
                    displayNameOf(userRef, subjectId),
                    sessions != null
                            ? sessions.count
                            : 0,
                    sessions != null
                            ? List.copyOf(sessions.nodeNames)
                            : List.of(),
                    sessions != null
                            ? sessions.lastAccessedMs
                            : null,
                    tokens.tokenCount(),
                    tokens.nextExpiryMs(),
                    tokens.latestExpiryMs()));
        }
        return rows;
    }

    private Map<String, SessionAggregate> aggregateSessions() {
        final Map<String, SessionAggregate> bySubject = new HashMap<>();
        for (final SessionDetails details : sessionListServiceProvider.get().listSessions().getValues()) {
            final UserRef userRef = details.getUserRef();
            // An unauthenticated session has no owner to attribute it to, so it cannot appear on a
            // subject-keyed list. It is still visible on the session list itself.
            final String subjectId = NullSafe.get(userRef, UserRef::getSubjectId);
            if (subjectId == null) {
                continue;
            }
            bySubject.computeIfAbsent(subjectId, k -> new SessionAggregate(userRef)).add(details);
        }
        return bySubject;
    }

    /**
     * Resolve a subject that only appeared in the token table.
     * <p>
     * Returns null rather than throwing when there is no stroom user: service and external subjects can hold
     * tokens without ever having been made one, and those rows must survive the merge - they are precisely the
     * accounts most worth being able to revoke.
     * </p>
     */
    private UserRef resolveUserRef(final String subjectId) {
        try {
            return securityContext.asProcessingUserResult(() ->
                            userServiceProvider.get().getUserBySubjectId(subjectId))
                    .map(User::asRef)
                    .orElse(null);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to resolve subject '" + subjectId + "' to a stroom user", e);
            return null;
        }
    }

    private static String displayNameOf(final UserRef userRef, final String subjectId) {
        final String displayName = NullSafe.get(userRef, UserRef::getDisplayName);
        return NullSafe.isBlankString(displayName)
                ? subjectId
                : displayName;
    }

    private static Predicate<UserAccessRow> activeFilter(final FindUserAccessCriteria criteria) {
        if (!criteria.isActiveOnly()) {
            return row -> true;
        }
        return row -> row.getSessionCount() > 0 || row.getTokenCount() > 0;
    }

    /**
     * Case-insensitive substring match over the display name and the subject id.
     * <p>
     * Deliberately simple rather than the full quick-filter grammar: the richer
     * {@code ExpressionPredicateFactory} lives in {@code stroom-query-common}, and depending on that from here
     * would pull the query language and LMDB into the security module to support one screen's filter. If
     * qualified-field filtering is wanted later, that is the upgrade path, and it is a deliberate cost.
     * </p>
     */
    private static Predicate<UserAccessRow> textFilter(final FindUserAccessCriteria criteria) {
        final String filter = criteria.getFilter();
        if (NullSafe.isBlankString(filter)) {
            return row -> true;
        }
        final String needle = filter.trim().toLowerCase(Locale.ROOT);
        return row -> contains(row.getDisplayName(), needle) || contains(row.getSubjectId(), needle);
    }

    private static boolean contains(final String value, final String lowerCaseNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerCaseNeedle);
    }

    /**
     * Most recently active first, so whoever is using the system right now is at the top. Subjects with no
     * sessions - the token-only rows - sort after those with, rather than being lost at an arbitrary position.
     */
    private static Comparator<UserAccessRow> comparator() {
        return Comparator
                .comparing(UserAccessRow::getLastAccessedMs,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(UserAccessRow::getDisplayName,
                        Comparator.nullsLast(String::compareToIgnoreCase));
    }


// --------------------------------------------------------------------------------


    private static final class SessionAggregate {

        private final UserRef userRef;
        private final Set<String> nodeNames = new LinkedHashSet<>();
        private int count;
        private Long lastAccessedMs;

        private SessionAggregate(final UserRef userRef) {
            this.userRef = userRef;
        }

        private void add(final SessionDetails details) {
            count++;
            if (details.getNodeName() != null) {
                nodeNames.add(details.getNodeName());
            }
            final long accessed = details.getLastAccessedMs();
            if (lastAccessedMs == null || accessed > lastAccessedMs) {
                lastAccessedMs = accessed;
            }
        }

        @Override
        public boolean equals(final Object o) {
            return this == o;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(userRef);
        }
    }
}

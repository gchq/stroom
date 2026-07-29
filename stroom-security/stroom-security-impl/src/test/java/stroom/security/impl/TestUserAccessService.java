/*
 * Copyright 2016-2026 Crown Copyright
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
import stroom.security.shared.SessionListResponse;
import stroom.security.shared.User;
import stroom.security.shared.UserAccessRow;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The merge is the whole substance of this class, and the cases that matter are the asymmetric ones - a subject
 * present on only one side. Getting either wrong makes somebody's access invisible, and therefore unrevokable.
 */
@ExtendWith(MockitoExtension.class)
class TestUserAccessService {

    @Mock
    private SessionListService sessionListService;
    @Mock
    private TokenInventory tokenInventory;
    @Mock
    private UserService userService;
    @Mock
    private SecurityContext securityContext;

    private UserAccessService service;

    @BeforeEach
    void setUp() {
        lenient().when(securityContext.secureResult(any(AppPermission.class), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        lenient().when(securityContext.asProcessingUserResult(any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        lenient().when(userService.getUserBySubjectId(anyString())).thenReturn(Optional.empty());
        lenient().when(sessionListService.listSessions()).thenReturn(SessionListResponse.empty());
        lenient().when(tokenInventory.summariseUsableTokensBySubject()).thenReturn(Map.of());

        service = new UserAccessService(
                () -> sessionListService, () -> tokenInventory, () -> userService, securityContext);
    }

    @Test
    void subjectWithTokensButNoSessionStillAppears() {
        // The case that forced this screen to be keyed on the subject rather than the session. A closed browser
        // or an API client leaves live tokens and no session; if the row vanished, the tokens could not be found
        // in order to revoke them.
        when(tokenInventory.summariseUsableTokensBySubject())
                .thenReturn(Map.of("api-client", new TokenSummary(2, 1_000L, 9_000L)));

        final List<UserAccessRow> rows = find().getValues();

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getSubjectId()).isEqualTo("api-client");
        assertThat(rows.getFirst().getTokenCount()).isEqualTo(2);
        assertThat(rows.getFirst().getSessionCount()).isZero();
        assertThat(rows.getFirst().getLastAccessedMs()).isNull();
    }

    @Test
    void subjectWithASessionButNoTokensStillAppears() {
        // The ordinary external-IdP row: nothing is minted here, so the token side is always empty.
        when(sessionListService.listSessions()).thenReturn(sessions(
                session(userRef("alice"), 500L, "node1")));

        final List<UserAccessRow> rows = find().getValues();

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getSubjectId()).isEqualTo("alice");
        assertThat(rows.getFirst().getSessionCount()).isEqualTo(1);
        assertThat(rows.getFirst().getTokenCount()).isZero();
    }

    @Test
    void tokenSubjectWithNoStroomUserIsKeptAndFindableBySubjectId() {
        // Service and external subjects can hold tokens without ever being made a stroom user. Dropping them
        // when the name lookup fails would hide exactly the accounts most worth revoking.
        when(tokenInventory.summariseUsableTokensBySubject())
                .thenReturn(Map.of("svc-account-uuid", new TokenSummary(1, 1_000L, 1_000L)));
        when(userService.getUserBySubjectId("svc-account-uuid")).thenReturn(Optional.empty());

        final List<UserAccessRow> rows = find().getValues();

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getUserRef()).isNull();
        // Falls back to the subject id, so the row is both identifiable and matchable by the quick filter.
        assertThat(rows.getFirst().getDisplayName()).isEqualTo("svc-account-uuid");
        assertThat(find("svc-account").getValues()).hasSize(1);
    }

    @Test
    void tokenOnlySubjectIsResolvedToItsUserSoItCanBeFoundByName() {
        when(tokenInventory.summariseUsableTokensBySubject())
                .thenReturn(Map.of("bob-subject", new TokenSummary(1, 1_000L, 1_000L)));
        when(userService.getUserBySubjectId("bob-subject"))
                .thenReturn(Optional.of(user("bob-subject", "Bob Bobson")));

        assertThat(find("bob bob").getValues())
                .as("a token-only subject must be findable by display name, not just by opaque id")
                .hasSize(1);
    }

    @Test
    void sessionsAndTokensForTheSameSubjectMergeIntoOneRow() {
        when(sessionListService.listSessions()).thenReturn(sessions(
                session(userRef("alice"), 500L, "node1"),
                session(userRef("alice"), 900L, "node2")));
        when(tokenInventory.summariseUsableTokensBySubject())
                .thenReturn(Map.of("alice", new TokenSummary(3, 1_000L, 5_000L)));

        final List<UserAccessRow> rows = find().getValues();

        assertThat(rows).hasSize(1);
        final UserAccessRow row = rows.getFirst();
        assertThat(row.getSessionCount()).isEqualTo(2);
        assertThat(row.getNodeNames()).containsExactlyInAnyOrder("node1", "node2");
        // Most recent access across all of the subject's sessions.
        assertThat(row.getLastAccessedMs()).isEqualTo(900L);
        assertThat(row.getTokenCount()).isEqualTo(3);
        assertThat(row.getNextTokenExpiryMs()).isEqualTo(1_000L);
        assertThat(row.getLastTokenExpiryMs()).isEqualTo(5_000L);
    }

    @Test
    void unauthenticatedSessionIsNotAttributedToAnybody() {
        // No owner means no subject to key a row on. It remains visible on the session list itself.
        when(sessionListService.listSessions()).thenReturn(sessions(session(null, 500L, "node1")));

        assertThat(find().getValues()).isEmpty();
    }

    @Test
    void theFilterMatchesDisplayNameCaseInsensitively() {
        when(sessionListService.listSessions()).thenReturn(sessions(
                session(namedUserRef("alice", "Alice Smith"), 500L, "node1"),
                session(namedUserRef("bob", "Bob Bobson"), 500L, "node1")));

        assertThat(find("ALICE").getValues()).hasSize(1);
        assertThat(find("smith").getValues()).hasSize(1);
        assertThat(find("nobody").getValues()).isEmpty();
    }

    @Test
    void rowsAreSortedMostRecentlyActiveFirstWithTokenOnlyRowsLast() {
        when(sessionListService.listSessions()).thenReturn(sessions(
                session(userRef("older"), 100L, "node1"),
                session(userRef("newer"), 900L, "node1")));
        when(tokenInventory.summariseUsableTokensBySubject())
                .thenReturn(Map.of("token-only", new TokenSummary(1, 1L, 1L)));

        assertThat(find().getValues().stream().map(UserAccessRow::getSubjectId))
                .containsExactly("newer", "older", "token-only");
    }

    @Test
    void findRequiresManageUsers() {
        find();

        org.mockito.Mockito.verify(securityContext)
                .secureResult(org.mockito.ArgumentMatchers.eq(AppPermission.MANAGE_USERS_PERMISSION), any());
    }

    private ResultPage<UserAccessRow> find() {
        return find(null);
    }

    private ResultPage<UserAccessRow> find(final String filter) {
        final FindUserAccessCriteria criteria = new FindUserAccessCriteria();
        criteria.setFilter(filter);
        criteria.setPageRequest(new PageRequest(0, 100));
        return service.find(criteria);
    }

    private static SessionListResponse sessions(final SessionDetails... details) {
        return new SessionListResponse(List.of(details));
    }

    private static SessionDetails session(final UserRef userRef, final long lastAccessedMs, final String node) {
        return new SessionDetails(userRef, 0L, lastAccessedMs, "agent", node, "handle");
    }

    private static UserRef userRef(final String subjectId) {
        return UserRef.builder().uuid(subjectId).subjectId(subjectId).build();
    }

    private static UserRef namedUserRef(final String subjectId, final String displayName) {
        return UserRef.builder().uuid(subjectId).subjectId(subjectId).displayName(displayName).build();
    }

    private static User user(final String subjectId, final String displayName) {
        return User.builder().uuid(subjectId).subjectId(subjectId).displayName(displayName).build();
    }
}

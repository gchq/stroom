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

package stroom.ai.impl;

import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.shared.AiToolAccess;
import stroom.ai.shared.AiToolCategory;
import stroom.ai.shared.AiToolPolicy;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserGroupsService;
import stroom.security.api.UserService;
import stroom.security.shared.AppPermission;
import stroom.security.shared.User;
import stroom.util.exception.DataChangedException;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAiToolPolicyService {

    private static final UserRef ALICE = UserRef.builder().uuid("alice").subjectId("alice").build();
    private static final UserRef BOB = UserRef.builder().uuid("bob").subjectId("bob").build();
    private static final UserRef ANALYSTS = UserRef.builder().uuid("analysts").subjectId("Analysts").group(true)
            .build();
    private static final UserRef STAFF = UserRef.builder().uuid("staff").subjectId("Staff").group(true).build();

    private final FakeDao dao = new FakeDao();
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final UserService userService = Mockito.mock(UserService.class);
    private final UserGroupsService userGroupsService = Mockito.mock(UserGroupsService.class);
    private final AiToolPolicyService service = new AiToolPolicyService(dao, securityContext, () -> userService,
            () -> userGroupsService, () -> Mockito.mock(BuiltInTools.class));

    @BeforeEach
    void setUp() {
        Mockito.when(securityContext.secureResult(Mockito.eq(AppPermission.MANAGE_MCP_TOOL_POLICIES),
                Mockito.<Supplier<Object>>any())).thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1)
                .get());
        Mockito.when(securityContext.getUserIdentityForAudit()).thenReturn("admin");
        for (final UserRef userRef : List.of(ALICE, BOB, ANALYSTS, STAFF)) {
            final User user = Mockito.mock(User.class);
            Mockito.when(user.getUuid()).thenReturn(userRef.getUuid());
            Mockito.when(user.asRef()).thenReturn(userRef);
            Mockito.when(userService.loadByUuid(userRef.getUuid())).thenReturn(Optional.of(user));
        }
        // Alice is in Analysts, which is in Staff. Bob is in no group.
        Mockito.when(userGroupsService.getGroups(ALICE)).thenReturn(Set.of(ANALYSTS));
        Mockito.when(userGroupsService.getGroups(ANALYSTS)).thenReturn(Set.of(STAFF));
        Mockito.when(userGroupsService.getGroups(STAFF)).thenReturn(Set.of());
        Mockito.when(userGroupsService.getGroups(BOB)).thenReturn(Set.of());
    }

    @Test
    void testAUserGetsTheUnionOfThePoliciesThatCoverThem() {
        service.create(policy("Everyone reads").allUsers(true)
                .categories(List.of(AiToolCategory.CORE, AiToolCategory.SEARCH)).build());
        // Through a group of a group.
        service.create(policy("Staff write content").members(List.of(STAFF))
                .categories(List.of(AiToolCategory.CONTENT, AiToolCategory.SEARCH))
                .access(AiToolAccess.READ_WRITE).build());
        service.create(policy("Alice processes").members(List.of(ALICE))
                .categories(List.of(AiToolCategory.PROCESSING)).build());
        // Disabled policies grant nothing.
        service.create(policy("Administration (off)").members(List.of(ALICE)).enabled(false)
                .categories(List.of(AiToolCategory.ADMINISTRATION)).access(AiToolAccess.READ_WRITE).build());

        assertThat(service.grantsFor(ALICE).byCategory()).isEqualTo(Map.of(
                AiToolCategory.CORE, AiToolAccess.READ_ONLY,
                AiToolCategory.SEARCH, AiToolAccess.READ_WRITE,
                AiToolCategory.CONTENT, AiToolAccess.READ_WRITE,
                AiToolCategory.PROCESSING, AiToolAccess.READ_ONLY));
        assertThat(service.grantsFor(BOB).byCategory()).isEqualTo(Map.of(
                AiToolCategory.CORE, AiToolAccess.READ_ONLY,
                AiToolCategory.SEARCH, AiToolAccess.READ_ONLY));
        assertThat(service.grantsFor(null)).isEqualTo(ToolGrants.NONE);
    }

    @Test
    void testAUserWithNoPolicyGetsNoTools() {
        service.create(policy("Analysts").members(List.of(ANALYSTS)).categories(List.of(AiToolCategory.CORE))
                .build());

        assertThat(service.grantsFor(BOB).isEmpty()).isTrue();
    }

    @Test
    void testAChangeIsSeenAtOnceOnThisNodeAndListenersAreTold() {
        final AtomicInteger told = new AtomicInteger();
        service.addChangeListener(told::incrementAndGet);
        final AiToolPolicy created = service.create(policy("Bob").members(List.of(BOB))
                .categories(List.of(AiToolCategory.CORE)).build());
        assertThat(service.grantsFor(BOB).access(AiToolCategory.CORE)).contains(AiToolAccess.READ_ONLY);

        service.update(created.getId(), created.copy().access(AiToolAccess.READ_WRITE).build());
        assertThat(service.grantsFor(BOB).access(AiToolCategory.CORE)).contains(AiToolAccess.READ_WRITE);

        service.delete(created.getId());
        assertThat(service.grantsFor(BOB).isEmpty()).isTrue();
        assertThat(told).hasValue(3);
    }

    @Test
    void testPoliciesReadBeforeAChangeAreNotKeptAsCurrent() {
        // A change saved while another thread read the policies: what it read is out of date.
        dao.whileListing = () -> service.create(policy("Bob").members(List.of(BOB))
                .categories(List.of(AiToolCategory.CORE)).build());

        assertThat(service.grantsFor(BOB).isEmpty()).isTrue();
        assertThat(service.grantsFor(BOB).access(AiToolCategory.CORE)).contains(AiToolAccess.READ_ONLY);
    }

    @Test
    void testPoliciesAreCheckedBeforeTheyAreSaved() {
        assertThatThrownBy(() -> service.create(policy(" ").categories(List.of(AiToolCategory.CORE)).build()))
                .hasMessageContaining("Give the policy a name");
        assertThatThrownBy(() -> service.create(policy("No categories").build()))
                .hasMessageContaining("at least one category");
        assertThatThrownBy(() -> service.create(policy("x".repeat(256)).categories(List.of(AiToolCategory.CORE))
                .build()))
                .hasMessageContaining("at most 255");
        assertThatThrownBy(() -> service.create(policy("Ghost").categories(List.of(AiToolCategory.CORE))
                .members(List.of(UserRef.builder().uuid("ghost").subjectId("ghost").build())).build()))
                .hasMessageContaining("There is no user or group");

        service.create(policy("Readers").categories(List.of(AiToolCategory.CORE)).build());
        assertThatThrownBy(() -> service.create(policy("READERS").categories(List.of(AiToolCategory.SEARCH))
                .build()))
                .hasMessageContaining("There is already a tool policy called 'READERS'");
    }

    @Test
    void testASavedPolicyIsTidiedAndShowsItsMembersNames() {
        final AiToolPolicy saved = service.create(policy("  Readers  ").description("  ")
                .members(List.of(UserRef.builder().uuid("alice").build(), ALICE))
                .categories(List.of(AiToolCategory.SEARCH, AiToolCategory.CORE, AiToolCategory.SEARCH)).build());

        assertThat(saved.getName()).isEqualTo("Readers");
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getMembers()).containsExactly(ALICE);
        assertThat(saved.getCategories()).containsExactly(AiToolCategory.CORE, AiToolCategory.SEARCH);
    }

    @Test
    void testAStaleUpdateIsRefused() {
        final AiToolPolicy created = service.create(policy("Readers").categories(List.of(AiToolCategory.CORE))
                .build());
        service.update(created.getId(), created.copy().description("first").build());

        assertThatThrownBy(() -> service.update(created.getId(), created.copy().description("second").build()))
                .isInstanceOf(DataChangedException.class);
        assertThatThrownBy(() -> service.update(created.getId(), created.copy().version(null).build()))
                .hasMessageContaining("version");
    }

    @Test
    void testManagingPoliciesNeedsThePermission() {
        Mockito.when(securityContext.secureResult(Mockito.eq(AppPermission.MANAGE_MCP_TOOL_POLICIES),
                Mockito.<Supplier<Object>>any())).thenThrow(new RuntimeException("Permission denied"));

        assertThatThrownBy(service::list).hasMessageContaining("Permission denied");
        assertThatThrownBy(() -> service.create(policy("x").categories(List.of(AiToolCategory.CORE)).build()))
                .hasMessageContaining("Permission denied");
        assertThatThrownBy(service::listTools).hasMessageContaining("Permission denied");
        // Asking what a user may use needs none: every run asks it.
        assertThat(service.grantsFor(BOB)).isEqualTo(ToolGrants.NONE);
    }

    private static AiToolPolicy.Builder policy(final String name) {
        return AiToolPolicy.builder().name(name);
    }


    // --------------------------------------------------------------------------------


    // Keeps policies in memory, as the database would.
    private static final class FakeDao implements AiToolPolicyDao {

        private final List<AiToolPolicy> policies = new ArrayList<>();
        private final AtomicInteger nextId = new AtomicInteger(1);
        // Run once, after the next list is read and before it is returned.
        private Runnable whileListing;

        @Override
        public List<AiToolPolicy> list() {
            final List<AiToolPolicy> read = policies.stream().sorted(Comparator.comparing(AiToolPolicy::getName))
                    .toList();
            final Runnable hook = whileListing;
            whileListing = null;
            if (hook != null) {
                hook.run();
            }
            return read;
        }

        @Override
        public Optional<AiToolPolicy> fetch(final int id) {
            return policies.stream().filter(policy -> policy.getId() == id).findFirst();
        }

        @Override
        public AiToolPolicy create(final AiToolPolicy policy, final String user) {
            final AiToolPolicy saved = policy.copy().id(nextId.getAndIncrement()).version(1).createUser(user)
                    .updateUser(user).build();
            policies.add(saved);
            return saved;
        }

        @Override
        public AiToolPolicy update(final AiToolPolicy policy, final String user) {
            final AiToolPolicy current = fetch(policy.getId()).orElseThrow();
            if (!current.getVersion().equals(policy.getVersion())) {
                throw new DataChangedException("changed");
            }
            final AiToolPolicy saved = policy.copy().version(current.getVersion() + 1).updateUser(user).build();
            policies.set(policies.indexOf(current), saved);
            return saved;
        }

        @Override
        public boolean delete(final int id) {
            return policies.removeIf(policy -> policy.getId() == id);
        }
    }
}

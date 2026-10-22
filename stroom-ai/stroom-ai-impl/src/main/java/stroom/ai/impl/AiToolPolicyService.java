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
import stroom.ai.shared.AiToolSummary;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserGroupsService;
import stroom.security.api.UserService;
import stroom.security.shared.AppPermission;
import stroom.security.shared.User;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/// Tool policies: which categories of Stroom's tools each user may use, in the "Ask Stroom AI" chat and over MCP.
///
/// Managing them needs the Manage MCP Tool Policies permission. What a user may use ([#grantsFor(UserRef)]) is the
/// union of the enabled policies that cover all users, name the user, or name a group the user is in (directly or
/// through another group). The policies are read again at most every [#REFRESH_AFTER] on each node, and at once on
/// the node a change is made on.
@Singleton
public class AiToolPolicyService implements CurrentToolGrants {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiToolPolicyService.class);

    /// How long a node keeps the policies it read before reading them again, so a change made on another node
    /// reaches it within this time.
    static final Duration REFRESH_AFTER = Duration.ofSeconds(30);
    static final int MAX_NAME_LENGTH = 255;
    static final int MAX_DESCRIPTION_LENGTH = 1000;

    private final AiToolPolicyDao dao;
    private final SecurityContext securityContext;
    private final Provider<UserService> userServiceProvider;
    private final Provider<UserGroupsService> userGroupsServiceProvider;
    private final Provider<BuiltInTools> builtInToolsProvider;
    private final AtomicReference<Cached> cached = new AtomicReference<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    @Inject
    AiToolPolicyService(final AiToolPolicyDao dao,
                        final SecurityContext securityContext,
                        final Provider<UserService> userServiceProvider,
                        final Provider<UserGroupsService> userGroupsServiceProvider,
                        final Provider<BuiltInTools> builtInToolsProvider) {
        this.dao = dao;
        this.securityContext = securityContext;
        this.userServiceProvider = userServiceProvider;
        this.userGroupsServiceProvider = userGroupsServiceProvider;
        this.builtInToolsProvider = builtInToolsProvider;
    }

    /// @return Every policy, by name, with its members' names.
    public List<AiToolPolicy> list() {
        return secured(() -> dao.list().stream()
                .map(this::withMemberDetails)
                .toList());
    }

    /// @param id A policy's id.
    /// @return The policy, with its members' names.
    /// @throws IllegalArgumentException if there is none with that id.
    public AiToolPolicy fetch(final int id) {
        return secured(() -> dao.fetch(id)
                .map(this::withMemberDetails)
                .orElseThrow(() -> new IllegalArgumentException("There is no tool policy " + id + ".")));
    }

    /// @param policy A new policy.
    /// @return The policy as saved.
    /// @throws IllegalArgumentException if it isn't valid, e.g. it has no name or no categories.
    public AiToolPolicy create(final AiToolPolicy policy) {
        return secured(() -> {
            final AiToolPolicy checked = checked(policy, null);
            final AiToolPolicy saved = dao.create(checked, securityContext.getUserIdentityForAudit());
            LOGGER.info(() -> "Created tool policy '" + saved.getName() + "'");
            changed();
            return withMemberDetails(saved);
        });
    }

    /// @param id     The policy's id.
    /// @param policy The policy as changed, with the version it was read at.
    /// @return The policy as saved.
    /// @throws IllegalArgumentException if it isn't valid.
    /// @throws stroom.util.exception.DataChangedException if it was changed since it was read.
    public AiToolPolicy update(final int id, final AiToolPolicy policy) {
        return secured(() -> {
            if (policy.getVersion() == null) {
                throw new IllegalArgumentException("Give the version of the policy that was changed.");
            }
            final AiToolPolicy checked = checked(policy.copy().id(id).build(), id);
            final AiToolPolicy saved = dao.update(checked, securityContext.getUserIdentityForAudit());
            LOGGER.info(() -> "Updated tool policy '" + saved.getName() + "'");
            changed();
            return withMemberDetails(saved);
        });
    }

    /// @param id A policy's id.
    /// @return True if it was deleted.
    public boolean delete(final int id) {
        return secured(() -> {
            final boolean deleted = dao.delete(id);
            if (deleted) {
                LOGGER.info(() -> "Deleted tool policy " + id);
                changed();
            }
            return deleted;
        });
    }

    /// @return Stroom's own tools, with their categories, for choosing what a policy grants.
    public List<AiToolSummary> listTools() {
        return secured(() -> builtInToolsProvider.get().summaries());
    }

    /// What a user's tool policies grant them. Needs no permission: every run and MCP call asks it for its user.
    ///
    /// @param userRef A user.
    /// @return The categories the user may use, and how far.
    public ToolGrants grantsFor(final UserRef userRef) {
        if (userRef == null) {
            return ToolGrants.NONE;
        }
        final Set<String> uuids = selfAndGroups(userRef);
        final Map<AiToolCategory, AiToolAccess> byCategory = new EnumMap<>(AiToolCategory.class);
        for (final AiToolPolicy policy : policies()) {
            if (policy.isEnabled() && covers(policy, uuids)) {
                for (final AiToolCategory category : policy.getCategories()) {
                    byCategory.merge(category, policy.getAccess(), AiToolAccess::or);
                }
            }
        }
        return new ToolGrants(byCategory);
    }

    /// @return What the current user's tool policies grant them.
    @Override
    public ToolGrants forCurrentUser() {
        return grantsFor(securityContext.getUserRef());
    }

    /// @param listener Told (on this node) when a policy has been created, changed or deleted here, e.g. so that
    ///                 an MCP session can tell its client its tools have changed.
    public void addChangeListener(final Runnable listener) {
        changeListeners.add(listener);
    }

    private static boolean covers(final AiToolPolicy policy, final Set<String> uuids) {
        return policy.isAllUsers()
               || policy.getMembers().stream().anyMatch(member -> uuids.contains(member.getUuid()));
    }

    // The user's own UUID and those of every group they are in, directly or through other groups.
    private Set<String> selfAndGroups(final UserRef userRef) {
        final UserGroupsService userGroupsService = userGroupsServiceProvider.get();
        final Set<String> uuids = new HashSet<>();
        final Deque<UserRef> toVisit = new ArrayDeque<>();
        toVisit.add(userRef);
        while (!toVisit.isEmpty()) {
            final UserRef next = toVisit.poll();
            if (next.getUuid() != null && uuids.add(next.getUuid())) {
                toVisit.addAll(NullSafe.set(userGroupsService.getGroups(next)));
            }
        }
        return uuids;
    }

    private List<AiToolPolicy> policies() {
        final long now = System.currentTimeMillis();
        final Cached current = cached.get();
        if (current != null && now - current.readTimeMs() < REFRESH_AFTER.toMillis()) {
            return current.policies();
        }
        final List<AiToolPolicy> policies = dao.list();
        cached.set(new Cached(policies, now));
        return policies;
    }

    private void changed() {
        cached.set(null);
        for (final Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (final RuntimeException e) {
                LOGGER.warn(() -> "A tool policy change listener failed", e);
            }
        }
    }

    // The policy, trimmed and checked, with its members as stored (their UUIDs).
    private AiToolPolicy checked(final AiToolPolicy policy, final Integer id) {
        final String name = NullSafe.trim(policy.getName());
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Give the policy a name.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("A policy's name may be at most " + MAX_NAME_LENGTH + " characters.");
        }
        final String description = NullSafe.trim(policy.getDescription());
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("A policy's description may be at most " + MAX_DESCRIPTION_LENGTH
                                               + " characters.");
        }
        if (policy.getCategories().isEmpty()) {
            throw new IllegalArgumentException("Choose at least one category of tools for the policy to grant.");
        }
        final boolean nameTaken = dao.list().stream()
                .anyMatch(other -> other.getName().equalsIgnoreCase(name) && !other.getId().equals(id));
        if (nameTaken) {
            throw new IllegalArgumentException("There is already a tool policy called '" + name + "'.");
        }
        final List<UserRef> members = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        for (final UserRef member : policy.getMembers()) {
            if (member == null || member.getUuid() == null) {
                continue;
            }
            if (seen.add(member.getUuid())) {
                final User user = userServiceProvider.get().loadByUuid(member.getUuid())
                        .orElseThrow(() -> new IllegalArgumentException("There is no user or group "
                                                                        + member.toDisplayString() + "."));
                members.add(UserRef.builder().uuid(user.getUuid()).build());
            }
        }
        return policy.copy()
                .name(name)
                .description(description.isEmpty()
                        ? null
                        : description)
                .members(members)
                .categories(policy.getCategories().stream().distinct().sorted().toList())
                .build();
    }

    // The policy, with each member's name and whether it is a group, as far as they can be found.
    private AiToolPolicy withMemberDetails(final AiToolPolicy policy) {
        final UserService userService = userServiceProvider.get();
        final List<UserRef> members = policy.getMembers().stream()
                .map(member -> userService.loadByUuid(member.getUuid())
                        .map(User::asRef)
                        .orElse(member))
                .toList();
        return policy.copy().members(members).build();
    }

    private <T> T secured(final Supplier<T> supplier) {
        return securityContext.secureResult(AppPermission.MANAGE_MCP_TOOL_POLICIES, supplier);
    }

    private record Cached(List<AiToolPolicy> policies, long readTimeMs) {

    }
}

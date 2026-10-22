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

package stroom.test;

import stroom.ai.impl.AiToolPolicyService;
import stroom.ai.shared.AiToolAccess;
import stroom.ai.shared.AiToolCategory;
import stroom.ai.shared.AiToolPolicy;
import stroom.security.api.AppPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.shared.AppPermission;
import stroom.security.shared.User;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/// An integration test of the REAL AI chat and MCP endpoint, with every one of their tools, on the test database
/// and with the real security context (see [AiCoreTestModule]). There is no user until a test makes one: work is
/// done as a user made with [#user] or [#administrator], or as the processing user.
@ExtendWith(GuiceExtension.class)
@IncludeModule(DbTestModule.class)
@IncludeModule(AiCoreTestModule.class)
public abstract class AbstractAiIntegrationTest extends StroomIntegrationTest {

    static {
        AbstractCoreIntegrationTest.extractLmdbLibraryToTempDir();
    }

    @Inject
    private SecurityContext securityContext;
    @Inject
    private UserService userService;
    @Inject
    private AppPermissionService appPermissionService;
    @Inject
    private AiToolPolicyService toolPolicyService;

    /// Makes a user (or finds the one made before) with the given application permissions.
    ///
    /// @param subjectId   The user's subject id.
    /// @param permissions Their permissions, besides any they had.
    /// @return The user.
    protected UserRef user(final String subjectId, final Set<AppPermission> permissions) {
        return securityContext.asProcessingUserResult(() -> {
            final User user = userService.getOrCreateUser(subjectId);
            permissions.forEach(permission -> appPermissionService.addPermission(user.asRef(), permission));
            return user.asRef();
        });
    }

    /// Makes an administrator (or finds the one made before).
    ///
    /// @return The administrator.
    protected UserRef administrator() {
        return user("admin", Set.of(AppPermission.ADMINISTRATOR));
    }

    /// Makes a tool policy granting every category of tools, as an administrator would in Tool Policies.
    ///
    /// @param name    The policy's name.
    /// @param access  How far it grants them.
    /// @param members The users or groups it grants them to.
    /// @return The policy, as saved.
    protected AiToolPolicy toolPolicy(final String name, final AiToolAccess access, final UserRef... members) {
        final AiToolPolicy policy = AiToolPolicy.builder()
                .name(name)
                .members(Arrays.asList(members))
                .categories(List.of(AiToolCategory.values()))
                .access(access)
                .build();
        return asUser(administrator(), () -> toolPolicyService.create(policy));
    }

    /// Does some work as a user.
    ///
    /// @param user The user.
    /// @param work The work.
    /// @param <T>  What it returns.
    /// @return What it returned.
    protected <T> T asUser(final UserRef user, final Supplier<T> work) {
        return securityContext.asUserResult(user, work);
    }

    /// Does some work as a user.
    ///
    /// @param user The user.
    /// @param work The work.
    protected void asUser(final UserRef user, final Runnable work) {
        securityContext.asUser(user, work);
    }
}

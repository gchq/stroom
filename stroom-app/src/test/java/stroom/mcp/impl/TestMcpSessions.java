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

package stroom.mcp.impl;

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiDao;
import stroom.ai.impl.McpEndpointConfig;
import stroom.ai.shared.AiToolAccess;
import stroom.job.api.ScheduledJob;
import stroom.mcp.impl.McpServletDriver.Background;
import stroom.mcp.impl.McpServletDriver.Reply;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.test.AbstractAiIntegrationTest;
import stroom.test.ConfigMapperSpy;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.shared.UserRef;
import stroom.util.time.StroomDuration;

import com.google.inject.Injector;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// The sessions of Stroom's REAL MCP endpoint, `/mcp`, as clients see them through Stroom's security filter and
/// the [McpServlet] (see [McpServletDriver]): how many a user may have open (`mcpEndpoint.maxSessionsPerUser`),
/// another user's session id, ending a session, and the "MCP Session Maintenance" job ending idle ones with their
/// workspaces (the hidden chats that hold them). A session that has ended is answered 404, as the MCP spec says
/// (Streamable HTTP, session management), so the client starts a new one.
class TestMcpSessions extends AbstractAiIntegrationTest {

    private static final String NO_SUCH_SESSION = "There is no such MCP session. Start a new one.";
    private static final Map<String, Object> READ = Map.of("folder", "System");

    @Inject
    private Injector injector;
    @Inject
    private ConfigMapperSpy configMapperSpy;
    @Inject
    private Map<ScheduledJob, Provider<Runnable>> scheduledJobs;
    @Inject
    private AiDao aiDao;
    @Inject
    private SecurityContext securityContext;

    private final AtomicInteger requestIds = new AtomicInteger(1);
    private McpServletDriver driver;
    private String suffix;

    @BeforeEach
    void setUp() {
        driver = new McpServletDriver(injector);
        // Users of this test's own, as sessions earlier tests started may still be open.
        suffix = UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void tearDown() {
        configMapperSpy.setConfigValueMappers(Map.of());
    }

    @Test
    void aUsersNewSessionPastTheLimitEndsTheirLeastRecentlyUsedOne() {
        endpoint(2, null);
        final Client alice = client("alice");
        final Client bob = client("bob");
        final String first = start(alice, false);
        assertServed(read(alice, first));
        final String second = start(alice, false);
        // Used after the first.
        ThreadUtil.sleepIgnoringInterrupts(20);
        assertServed(read(alice, second));
        final String bobs = start(bob, false);
        assertServed(read(bob, bobs));
        assertThat(workspaceOf(first)).isTrue();

        // Alice's third session starts, as the client asked...
        final String third = start(alice, false);
        assertServed(read(alice, third));

        // ...and her least recently used one has ended, with its workspace: its client is told there is no such
        // session, so it starts a new one.
        assertNoSuchSession(read(alice, first));
        assertThat(workspaceOf(first)).isFalse();
        assertServed(read(alice, second));
        // Bob's sessions don't count towards hers.
        assertServed(read(bob, bobs));
    }

    @Test
    void aNewSessionIsRefusedWhileEverySessionOfTheUserHasACallRunning() throws Exception {
        endpoint(1, null);
        final Client alice = client("alice");
        final String busy = start(alice, true);

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // A call that changes something waits for alice to answer its form.
            final Background call = driver.callInBackground(executor, alice.apiKey(), busy,
                    requestIds.incrementAndGet(), "create_feed", Map.of("build", "acme-busy-" + suffix,
                            "name", "ACME-BUSY-" + suffix.toUpperCase()));
            final Map<String, Object> form = awaitForm(call);

            // Her only session has a call running, so a new one is refused, saying why.
            final Reply refused = driver.initialize(alice.apiKey(), true);
            assertThat(refused.status()).as(refused.toString())
                    .isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            assertThat(refused.sessionId()).isNull();
            assertThat(refused.body()).contains("You have 1 MCP sessions open on this node, each with a call "
                                                + "running, as many as you may.");

            // She declines the form: the call ends without changing anything.
            final Reply answered = driver.answer(alice.apiKey(), busy, form.get("id"), Map.of("action", "decline"));
            assertThat(answered.status()).as(answered.toString()).isEqualTo(HttpServletResponse.SC_ACCEPTED);
            call.done().get(60, TimeUnit.SECONDS);
            assertThat(call.reply().toolText()).isNotNull().doesNotContain("\"uuid\"");
        } finally {
            executor.shutdownNow();
        }

        // Now the session is idle, a new one ends it to make room.
        final String next = start(alice, true);
        assertServed(read(alice, next));
        assertNoSuchSession(read(alice, busy));
    }

    @Test
    void anotherUsersSessionIdIsAnsweredAsNoSuchSessionAndLeavesTheSessionAlone() {
        final Client alice = client("alice");
        final Client mallory = client("mallory");
        final String session = start(alice, false);
        assertServed(read(alice, session));
        final Instant used = Instant.now();
        final StroomMcpServer server = injector.getInstance(StroomMcpServer.class);
        final int sessions = server.sessionCount();
        // Long enough for alice's session to be idle by the time the maintenance job looks.
        ThreadUtil.sleepIgnoringInterrupts(1_500);

        // Mallory, signed in as herself, with alice's session id: as far as she can tell there is no such session.
        assertNoSuchSession(read(mallory, session));
        final Reply deleted = driver.delete(mallory.apiKey(), session);
        assertNoSuchSession(deleted);

        // Alice's session is still open, with its workspace.
        assertThat(server.isOpenFor(session, alice.user())).isTrue();
        assertThat(server.sessionCount()).isEqualTo(sessions);
        assertThat(workspaceOf(session)).isTrue();

        // Nor did mallory's requests count as using it: it was last used when alice used it, so a job keeping
        // sessions for a second ends it.
        assertThat(Duration.between(used, Instant.now())).isGreaterThan(Duration.ofSeconds(1));
        endpoint(null, StroomDuration.ofSeconds(1));
        runSessionMaintenance();
        assertNoSuchSession(read(alice, session));
    }

    @Test
    void aSessionTheClientEndsIsGoneWithItsWorkspace() {
        final Client alice = client("alice");
        final String session = start(alice, false);
        assertServed(read(alice, session));
        assertThat(workspaceOf(session)).isTrue();

        final Reply deleted = driver.delete(alice.apiKey(), session);

        assertThat(deleted.status()).as(deleted.toString()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(workspaceOf(session)).isFalse();
        assertNoSuchSession(read(alice, session));
        assertNoSuchSession(driver.delete(alice.apiKey(), session));
        assertThat(injector.getInstance(StroomMcpServer.class).isOpenFor(session, alice.user())).isFalse();
    }

    @Test
    void theMaintenanceJobEndsIdleSessionsWithTheirWorkspacesAndKeepsLiveOnes() {
        final Client alice = client("alice");
        final String idle = start(alice, false);
        assertServed(read(alice, idle));
        ThreadUtil.sleepIgnoringInterrupts(2_500);
        final String live = start(alice, false);
        assertServed(read(alice, live));
        // Sessions idle for two seconds are ended.
        endpoint(null, StroomDuration.ofSeconds(2));

        runSessionMaintenance();

        assertThat(workspaceOf(idle)).isFalse();
        assertNoSuchSession(read(alice, idle));
        assertThat(workspaceOf(live)).isTrue();
        assertServed(read(alice, live));
    }

    // The MCP endpoint's config, with these changes (null for the default).
    private void endpoint(final Integer maxSessionsPerUser, final StroomDuration sessionRetention) {
        configMapperSpy.setConfigValueMapper(AiConfig.class, config -> AiConfigChanges.withMcpEndpoint(config,
                new McpEndpointConfig(null, null, null, null, sessionRetention, maxSessionsPerUser)));
    }

    // A user who may use every tool over MCP, with an API key for their client.
    private Client client(final String name) {
        final UserRef user = user(name + "-" + suffix, Set.of(AppPermission.USE_MCP_TOOLS,
                AppPermission.ADMINISTRATOR));
        toolPolicy("Sessions " + name + " " + suffix, AiToolAccess.READ_WRITE, user);
        return new Client(user, driver.apiKey(user));
    }

    private String start(final Client client, final boolean canShowForms) {
        final Reply started = driver.initialize(client.apiKey(), canShowForms);
        assertThat(started.status()).as(started.toString()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(started.body()).contains("\"serverInfo\"");
        assertThat(started.sessionId()).isNotBlank();
        return started.sessionId();
    }

    // A call that only reads, which gives the session its workspace (if it has none yet).
    private Reply read(final Client client, final String sessionId) {
        return driver.call(client.apiKey(), sessionId, requestIds.incrementAndGet(), "folder_contents", READ);
    }

    private boolean workspaceOf(final String sessionId) {
        return securityContext.asProcessingUserResult(() -> aiDao.findSessionChat(sessionId)).isPresent();
    }

    private void runSessionMaintenance() {
        final Runnable job = scheduledJobs.entrySet().stream()
                .filter(entry -> McpModule.SESSION_JOB_NAME.equals(entry.getKey().getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No job named " + McpModule.SESSION_JOB_NAME))
                .getValue()
                .get();
        securityContext.asProcessingUser(job);
    }

    // Waits for the call's form (MCP elicitation) to reach the client, in the call's stream.
    private static Map<String, Object> awaitForm(final Background call) {
        final Instant giveUp = Instant.now().plusSeconds(60);
        while (Instant.now().isBefore(giveUp)) {
            if (call.reply().body().contains("elicitation/create")) {
                final Map<String, Object> form = call.reply().serverRequest("elicitation/create");
                if (form != null) {
                    return form;
                }
            }
            assertThat(call.done()).as("The call ended without a form: " + call.reply()).isNotDone();
            ThreadUtil.sleepIgnoringInterrupts(50);
        }
        throw new AssertionError("No form reached the client: " + call.reply());
    }

    private static void assertServed(final Reply reply) {
        assertThat(reply.status()).as(reply.toString()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(reply.toolText()).as(reply.toString()).isNotNull().doesNotStartWith("Error");
    }

    private static void assertNoSuchSession(final Reply reply) {
        assertThat(reply.status()).as(reply.toString()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        assertThat(reply.error()).isEqualTo(NO_SUCH_SESSION);
    }


    // --------------------------------------------------------------------------------


    /// An MCP client of a user.
    ///
    /// @param user   The user.
    /// @param apiKey Their API key, which signs the client's requests in.
    private record Client(UserRef user, String apiKey) {

    }
}
